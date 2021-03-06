package tud.seemuh.nfcgate.nfc.config;

public class ConfigOption {
    private OptionType mID;
    private byte[] mData;

    public ConfigOption(OptionType ID, byte[] data) {
        mID = ID;
        mData = data;
    }

    public ConfigOption(OptionType ID, byte data) {
        this(ID, new byte[] { data });
    }

    public int len() {
        return mData.length;
    }

    public void push(byte[] data, int offset) {
        data[offset + 0] = mID.getID();
        data[offset + 1] = (byte)mData.length;

        System.arraycopy(mData, 0, data, offset + 2, mData.length);
    }

    // from https://stackoverflow.com/a/9855338/207861
    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();

        result.append("Type: ");
        result.append(mID.toString());

        if (mData.length > 1) {
            result.append(" (");
            result.append(mData.length);
            result.append(")");
        }

        result.append(", Value: 0x");
        result.append(bytesToHex(mData));
        result.append("\n");

        return result.toString();
    }
}
