package se.kth.ndb.test;

import com.mysql.clusterj.annotation.*;

@PersistenceCapable(table = "test")
@PartitionKey(column = "partition_id")
public interface Table {
  @PrimaryKey
  @Column(name = "partition_id")
  int getPartitionId();
  void setPartitionId(int partitionId);

  @PrimaryKey
  @Column(name = "id")
  int getId();
  void setId(int id);

  @Column(name = "data1")
  @Index(name = "dindex")
  int getData1();
  void setData1(int data);

  @Column(name = "data2")
  int getData2();
  void setData2(int data);

  @Column(name = "longdata")
  byte[] getLongData();
  void setLongData(byte[] longdata);
}

