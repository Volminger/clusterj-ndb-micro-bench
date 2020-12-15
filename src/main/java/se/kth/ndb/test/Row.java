package se.kth.ndb.test;

public class Row {
    private int pratitionKey;
    private int id;
    private int data1;
    private int data2;
    private byte[] longdata;

    public Row(int pratitionKey, int id, int data1, int data2) {
        this.pratitionKey = pratitionKey;
        this.id = id;
        this.data1 = data1;
        this.data2 = data2;
        this.longdata = null;
    }

    public int getPratitionKey() {
        return pratitionKey;
    }

    public void setPratitionKey(int pratitionKey) {
        this.pratitionKey = pratitionKey;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getData1() {
        return data1;
    }

    public void setData1(int data1) {
        this.data1 = data1;
    }

    public int getData2() {
        return data2;
    }

    public void setData2(int data2) {
        this.data2 = data2;
    }

    public byte[] getLongData() {
	return longdata;
    }

    public void setLongData(byte[] longdata) {
	this.longdata = longdata;
    }

    @Override
    public String toString() {
        return "Row{" +
                "pratitionKey=" + pratitionKey +
                ", id=" + id +
                ", data1=" + data1 +
                ", data2=" + data2 +
                "}\n";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Row row = (Row) o;

        if (pratitionKey != row.pratitionKey) return false;
        if (id != row.id) return false;
        if (data1 != row.data1) return false;
        return data2 == row.data2;
    }

    @Override
    public int hashCode() {
        int result = pratitionKey;
        result = 31 * result + id;
        result = 31 * result + data1;
        result = 31 * result + data2;
        return result;
    }
}
