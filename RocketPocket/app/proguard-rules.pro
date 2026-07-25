# Reflection fallback used by BluetoothController when the SDP record lookup fails.
-keepclassmembers class android.bluetooth.BluetoothDevice {
    public android.bluetooth.BluetoothSocket createRfcommSocket(int);
}
