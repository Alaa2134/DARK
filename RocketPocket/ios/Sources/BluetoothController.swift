import Combine
import CoreBluetooth
import Foundation

/// Owns the BLE link to the car.
///
/// iOS forbids third-party apps from using Bluetooth Classic (SPP) entirely, so this speaks BLE
/// over the Nordic UART Service instead — the same single characters, a different transport. The
/// car must therefore be running `RocketPocket_ESP32_BLE.ino` rather than the Classic sketch.
@MainActor
final class BluetoothController: NSObject, ObservableObject {

    static let carName = "Rocket Pocket"

    /// Nordic UART Service. RX/TX are named from the car's point of view: the phone writes to
    /// RX, and the car sends its telemetry as notifications on TX.
    private static let serviceUUID = CBUUID(string: "6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    private static let rxUUID = CBUUID(string: "6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
    private static let txUUID = CBUUID(string: "6E400003-B5A3-F393-E0A9-E50E24DCCA9E")

    static let connectionLostMessage = "Bluetooth Connection Lost"

    @Published private(set) var connectionState: ConnectionState = .disconnected
    @Published private(set) var connectedCarName: String?
    @Published private(set) var discovered: [DiscoveredCar] = []
    @Published private(set) var isScanning = false

    /// The last character actually written, paired with a counter so the UI flashes even when
    /// the same character is sent twice running.
    @Published private(set) var lastSent: (character: Character, counter: UInt64)?
    @Published private(set) var lastReceived: String?

    let messages = PassthroughSubject<String, Never>()

    private var central: CBCentralManager!
    private var car: CBPeripheral?
    private var rxCharacteristic: CBCharacteristic?

    private var sendCounter: UInt64 = 0
    private var lineBuffer = ""
    private var peripherals: [UUID: CBPeripheral] = [:]

    override init() {
        super.init()
        central = CBCentralManager(delegate: self, queue: .main)
    }

    // MARK: - Scanning

    func startScan() {
        guard central.state == .poweredOn else {
            messages.send(bluetoothUnavailableReason())
            return
        }
        discovered.removeAll()
        peripherals.removeAll()
        isScanning = true
        central.scanForPeripherals(withServices: [Self.serviceUUID], options: nil)

        // BLE scanning is battery-hungry and the car advertises continuously, so a short window
        // is plenty; leaving it running would drain the phone mid-race for nothing.
        Task { [weak self] in
            try? await Task.sleep(nanoseconds: 8_000_000_000)
            self?.stopScan()
        }
    }

    func stopScan() {
        guard isScanning else { return }
        central.stopScan()
        isScanning = false
    }

    private func bluetoothUnavailableReason() -> String {
        switch central.state {
        case .poweredOff: return "Turn Bluetooth on to continue"
        case .unauthorized: return "Allow Bluetooth for this app in Settings"
        case .unsupported: return "This device has no Bluetooth LE"
        default: return "Bluetooth is not ready yet"
        }
    }

    // MARK: - Connecting

    func connect(to car: DiscoveredCar) {
        guard let peripheral = peripherals[car.id] else { return }
        stopScan()
        connectionState = .connecting
        connectedCarName = car.name
        self.car = peripheral
        peripheral.delegate = self
        central.connect(peripheral, options: nil)
    }

    func disconnect() {
        sendImmediately(Command.stop)
        if let peripheral = car {
            central.cancelPeripheralConnection(peripheral)
        }
        teardown()
        messages.send("Disconnected")
    }

    private func teardown() {
        rxCharacteristic = nil
        car = nil
        connectionState = .disconnected
        connectedCarName = nil
        lineBuffer = ""
    }

    // MARK: - Sending

    /// Writes a single character. Returns false when there is no live link, which is what keeps
    /// movement commands from being sent while disconnected.
    @discardableResult
    func send(_ command: Character) -> Bool {
        guard connectionState.isConnected,
              let peripheral = car,
              let characteristic = rxCharacteristic,
              let data = String(command).data(using: .utf8)
        else { return false }

        // Without response: a movement command arriving a few milliseconds sooner matters more
        // than confirmation that it arrived, and the app repeats the held command anyway.
        let type: CBCharacteristicWriteType =
            characteristic.properties.contains(.writeWithoutResponse) ? .withoutResponse : .withResponse
        peripheral.writeValue(data, for: characteristic, type: type)

        sendCounter &+= 1
        lastSent = (command, sendCounter)
        return true
    }

    /// Used when the app is going to the background or being torn down, where a queued write
    /// might never run and would leave the car driving.
    func sendImmediately(_ command: Character) {
        _ = send(command)
    }

    // MARK: - Receiving

    private func handle(data: Data) {
        guard let chunk = String(data: data, encoding: .utf8) else { return }
        for character in chunk {
            if character == "\n" || character == "\r" {
                let line = lineBuffer.trimmingCharacters(in: .whitespaces)
                lineBuffer = ""
                if !line.isEmpty { lastReceived = line }
            } else {
                lineBuffer.append(character)
                // Guard against a chatty peer that never sends a newline.
                if lineBuffer.count > 128 { lineBuffer = "" }
            }
        }
    }
}

// MARK: - CBCentralManagerDelegate

extension BluetoothController: CBCentralManagerDelegate {

    nonisolated func centralManagerDidUpdateState(_ central: CBCentralManager) {
        Task { @MainActor in
            if central.state != .poweredOn, self.connectionState != .disconnected {
                self.teardown()
                self.messages.send(Self.connectionLostMessage)
            }
        }
    }

    nonisolated func centralManager(
        _ central: CBCentralManager,
        didDiscover peripheral: CBPeripheral,
        advertisementData: [String: Any],
        rssi RSSI: NSNumber
    ) {
        let name = (advertisementData[CBAdvertisementDataLocalNameKey] as? String)
            ?? peripheral.name
            ?? "Unknown device"
        let id = peripheral.identifier
        let rssi = RSSI.intValue

        Task { @MainActor in
            self.peripherals[id] = peripheral
            let entry = DiscoveredCar(id: id, name: name, rssi: rssi)
            if let index = self.discovered.firstIndex(where: { $0.id == id }) {
                self.discovered[index] = entry
            } else {
                self.discovered.append(entry)
            }
            // Float the car to the top so it can be picked at a glance between heats.
            self.discovered.sort {
                if $0.isRocketPocket != $1.isRocketPocket { return $0.isRocketPocket }
                return $0.rssi > $1.rssi
            }
        }
    }

    nonisolated func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        peripheral.discoverServices([Self.serviceUUID])
    }

    nonisolated func centralManager(
        _ central: CBCentralManager,
        didFailToConnect peripheral: CBPeripheral,
        error: Error?
    ) {
        Task { @MainActor in
            self.teardown()
            self.messages.send("Connection failed: \(error?.localizedDescription ?? "unknown error")")
        }
    }

    nonisolated func centralManager(
        _ central: CBCentralManager,
        didDisconnectPeripheral peripheral: CBPeripheral,
        error: Error?
    ) {
        Task { @MainActor in
            guard self.connectionState != .disconnected else { return }
            self.teardown()
            self.messages.send(Self.connectionLostMessage)
        }
    }
}

// MARK: - CBPeripheralDelegate

extension BluetoothController: CBPeripheralDelegate {

    nonisolated func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard let service = peripheral.services?.first(where: { $0.uuid == Self.serviceUUID }) else {
            Task { @MainActor in
                self.teardown()
                self.messages.send("That device is not a Rocket Pocket car")
            }
            return
        }
        peripheral.discoverCharacteristics([Self.rxUUID, Self.txUUID], for: service)
    }

    nonisolated func peripheral(
        _ peripheral: CBPeripheral,
        didDiscoverCharacteristicsFor service: CBService,
        error: Error?
    ) {
        let characteristics = service.characteristics ?? []
        let rx = characteristics.first { $0.uuid == Self.rxUUID }
        let tx = characteristics.first { $0.uuid == Self.txUUID }

        if let tx { peripheral.setNotifyValue(true, for: tx) }

        Task { @MainActor in
            guard let rx else {
                self.teardown()
                self.messages.send("The car did not expose its command channel")
                return
            }
            self.rxCharacteristic = rx
            self.connectionState = .connected
            self.messages.send("Connected to \(self.connectedCarName ?? Self.carName)")
            // Start from a known-safe state: the car must not be moving on connect.
            self.send(Command.stop)
        }
    }

    nonisolated func peripheral(
        _ peripheral: CBPeripheral,
        didUpdateValueFor characteristic: CBCharacteristic,
        error: Error?
    ) {
        guard characteristic.uuid == Self.txUUID, let data = characteristic.value else { return }
        Task { @MainActor in
            self.handle(data: data)
        }
    }
}
