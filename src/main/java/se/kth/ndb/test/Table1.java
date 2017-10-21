package se.kth.ndb.test;

import com.mysql.clusterj.annotation.Column;
import com.mysql.clusterj.annotation.PartitionKey;
import com.mysql.clusterj.annotation.PersistenceCapable;
import com.mysql.clusterj.annotation.PrimaryKey;

@PersistenceCapable(table = "table1")
@PartitionKey(column = "partition_id")
public interface Table1DTO extends Table {
  @PrimaryKey
  @Column(name = "partition_id")
  int getPartitionId();
  void setPartitionId(int partitionId);

  @PrimaryKey
  @Column(name = "id")
  int getId();
  void setId(int id);

  @Column(name = "data")
  int getData();
  void setData(int data);
}

