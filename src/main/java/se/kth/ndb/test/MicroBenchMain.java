package se.kth.ndb.test;


import com.mysql.clusterj.ClusterJHelper;
import com.mysql.clusterj.LockMode;
import com.mysql.clusterj.SessionFactory;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.FileWriter;
import java.io.IOException;
import java.text.NumberFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.math3.stat.descriptive.SynchronizedDescriptiveStatistics;

public class MicroBenchMain {
  @Option(name = "-numThreads", usage = "Number of threads. Default is 1")
  private static int numThreads = 1;

  @Option(name = "-lockMode", usage = "Lock Mode. (RC, S, E). READ_COMMITTED, SHARED, EXCLUSIVE. Default is RC")
  private static String lockModeStr = "RC";
  private static LockMode lockMode = LockMode.READ_COMMITTED;

  @Option(name = "-microBenchType", usage = "PK, BATCH, PPIS, IS, FTS")
  private static String microBenchTypeStr = "PK";
  private static MicroBenchType microBenchType = MicroBenchType.PK;

  @Option(name = "-nonDistributedPKOps", usage = "For each thread all the operations in a PK/BATCH test will be performed on a single partition")
  private static boolean nonDistributedBatch = false;

  @Option(name = "-distributedPKOps", usage = "For each thread all the operations in the PK/BATCH test operations will go to different database partition")
  private static boolean distributedBatch = false;

  @Option(name = "-schema", usage = "DB schemma name. Default is test")
  static private String schema = "test";

  @Option(name = "-dbHost", usage = "com.mysql.clusterj.connectstring.")
  static private String dbHost = "";

  @Option(name = "-rowsPerTx", usage = "Number of rows that are read in each transaction")
  static private int rowsPerTx = 1;

  @Option(name = "-maxOperationsToPerform", usage = "Total operations to perform. Default is 1000. Recommended 1 million or more")
  static private long maxOperationsToPerform = 100;

  @Option(name = "-clientId", usage = "Id of this application. In case of distributed deployment each instance of this benchmark should have a unique id")
  static private int clientId = 0;

  @Option(name = "-createDummyData", usage = "Create dummy data")
  static private boolean createDummyData = false;

  @Option(name = "-numDummyRows", usage = "Number of dummy rows to create")
  static private int numDummyRows = 1000;

  private AtomicInteger opsCompleted = new AtomicInteger(0);
  private AtomicInteger successfulOps = new AtomicInteger(0);
  private AtomicInteger failedOps = new AtomicInteger(0);
  private AtomicInteger speed = new AtomicInteger(0);
  private static long lastOutput = 0;
  private SynchronizedDescriptiveStatistics latency = new SynchronizedDescriptiveStatistics();

  Random rand = new Random(System.currentTimeMillis());
  ExecutorService executor = null;
  SessionFactory sf = null;
  @Option(name = "-help", usage = "Print usages")
  private boolean help = false;

  private Worker[] workers;


  public void startApplication(String[] args) throws Exception {
    parseArgs(args);

    setUpDBConnection();

    if(createDummyData) {
      writeDummyData();
    }

    createWorkers();

    writeData();

    System.out.println("Press enter to start execution");
    System.in.read();
    long startTime = System.currentTimeMillis();
    startMicroBench();
    long totExeTime = (System.currentTimeMillis()-startTime);

    long speed = (long)((successfulOps.get()/(double)totExeTime)*1000);

    String msg = "Speed: "+speed+" ops/sec.\t\tAvg Op Latency: "+latency.getMean()/1000000;
    blueColoredText(msg);
    FileWriter out = new FileWriter("result.txt", false);
    out.write(msg + "\n");
    out.close();

  }

  private void parseArgs(String[] args) {
    CmdLineParser parser = new CmdLineParser(this);
    parser.setUsageWidth(80);
    try {
      // parse the arguments.
      parser.parseArgument(args);
      if (lockModeStr.compareToIgnoreCase("E") == 0) {
        lockMode = LockMode.EXCLUSIVE;
      } else if (lockModeStr.compareToIgnoreCase("S") == 0) {
        lockMode = LockMode.SHARED;
      } else if (lockModeStr.compareToIgnoreCase("RC") == 0) {
        lockMode = LockMode.READ_COMMITTED;
      } else {
        showHelp(parser, true);
      }

      if (microBenchTypeStr.compareToIgnoreCase("PK") == 0) {
        microBenchType = MicroBenchType.PK;
      } else if (microBenchTypeStr.compareToIgnoreCase("BATCH") == 0) {
        microBenchType = MicroBenchType.BATCH;
        if ((distributedBatch && nonDistributedBatch) ||
                (!distributedBatch && !nonDistributedBatch)) {
          System.out.println("Seletect One. Distributed/Non Distributed batch Operations");
          showHelp(parser, true);
        }
      } else if (microBenchTypeStr.compareToIgnoreCase("PPIS") == 0) {
        microBenchType = MicroBenchType.PPIS;
      } else if (microBenchTypeStr.compareToIgnoreCase("IS") == 0) {
        microBenchType = MicroBenchType.IS;
      } else if (microBenchTypeStr.compareToIgnoreCase("FTS") == 0) {
        microBenchType = MicroBenchType.FTS;
      } else {
        System.out.println("Wrong bench mark type");
        showHelp(parser, true);
      }

    } catch (Exception e) {
      showHelp(parser, true);
    }

    if (help) {
      showHelp(parser, true);
    }
  }

  private void showHelp(CmdLineParser parser, boolean kill) {
    parser.printUsage(System.err);
    if (kill) {
      System.exit(0);
    }
  }

  public void setUpDBConnection() throws Exception {
    Properties props = new Properties();
    props.setProperty("com.mysql.clusterj.connectstring", dbHost);
    props.setProperty("com.mysql.clusterj.database", schema);
    props.setProperty("com.mysql.clusterj.connect.retries", "4");
    props.setProperty("com.mysql.clusterj.connect.delay", "5");
    props.setProperty("com.mysql.clusterj.connect.verbose", "1");
    props.setProperty("com.mysql.clusterj.connect.timeout.before", "30");
    props.setProperty("com.mysql.clusterj.connect.timeout.after", "20");
    props.setProperty("com.mysql.clusterj.max.transactions", "1024");
    props.setProperty("com.mysql.clusterj.connection.pool.size", "1");
    sf = ClusterJHelper.getSessionFactory(props);
  }

  public void writeDummyData() throws InterruptedException, IOException {
    if(createDummyData){
      workers = new Worker[numThreads];
      executor = Executors.newFixedThreadPool(numThreads);

      int rowsPerThread = numDummyRows / numThreads;
      int rowsStartId = 10000000;
      DummyDataWriter[] workers = new DummyDataWriter[numThreads];
      for (int i = 0; i < numThreads; i++){
        int threadRowsStartId = (rowsStartId+ (i*rowsPerThread));
        int threadRowsEndId = threadRowsStartId + rowsPerThread;
        workers[i] = ( new DummyDataWriter(0,successfulOps,speed,sf,threadRowsStartId, threadRowsEndId ));
      }

      for (int i = 0; i < numThreads; i++) {
        executor.execute(workers[i]);
      }
      executor.shutdown();
      long startTime = System.currentTimeMillis();
      while (!executor.isTerminated()) {
        Thread.sleep(1000);
        System.out.println("Writing speed: "+speed+" ops/sec");
        speed.set(0);
      }
      System.exit(0);
    }
  }

  public void createWorkers() throws InterruptedException, IOException {
    workers = new Worker[numThreads];
    executor = Executors.newFixedThreadPool(numThreads);

    int threadIdStart = (clientId * numThreads);
    int existingRows = (clientId * numThreads * rowsPerTx);
    for (int i = 0; i < numThreads; i++) {
      int threadId = threadIdStart + i;
      int rowStartId = existingRows + (i * rowsPerTx);
      Worker worker = new Worker((threadIdStart + i), opsCompleted, successfulOps, failedOps, speed,
              maxOperationsToPerform, microBenchType, sf, rowStartId, rowsPerTx, distributedBatch, lockMode,
              latency);
      workers[i] = worker;
    }
  }


  public void writeData() throws Exception {
    for (int i = 0; i < numThreads; i++) {
      workers[i].writeData();
    }
  }

  public void startMicroBench() throws InterruptedException, IOException {
    for (int i = 0; i < numThreads; i++) {
      executor.execute(workers[i]);
    }
    executor.shutdown();
    long startTime = System.currentTimeMillis();
    while (!executor.isTerminated()) {
      Thread.sleep(1000);
      printMemUsageAndSpeed();
    }
  }

  private void printMemUsageAndSpeed() {
    long curTime = System.currentTimeMillis();
    if ((curTime - lastOutput) > 1000) {
      //StringBuilder sb = systemStats();
      StringBuilder sb = new StringBuilder("");
      sb.append("Speed: " + speed + " ops/sec. Successful Ops: " + successfulOps + " Failed Ops: " + failedOps);
      speed.set(0);
      System.out.println(sb.toString());
      lastOutput = curTime;
    }
  }

  public static StringBuilder systemStats() {
    Runtime runtime = Runtime.getRuntime();
    NumberFormat format = NumberFormat.getInstance();
    StringBuilder sb = new StringBuilder();
    long maxMemory = runtime.maxMemory();
    long allocatedMemory = runtime.totalMemory();
    long freeMemory = runtime.freeMemory();

    sb.append("\nFree Mem: " + format.format(freeMemory / (1024 * 1024)) + " MB. ");
    sb.append("Allocated Mem: " + format.format(allocatedMemory / (1024 * 1024)) + " MB. ");
    sb.append("Max Mem: " + format.format(maxMemory / (1024 * 1024)) + " MB. ");
    sb.append("Tot Free Mem: " + format.format((freeMemory + (maxMemory - allocatedMemory)) / (1024 * 1024)) + " MB. ");
    sb.append("Direct Mem: " + sun.misc.SharedSecrets.getJavaNioAccess().getDirectBufferPool().getMemoryUsed() / (1024 * 1024) + " MB. \n");
    return sb;
  }

  private void redColoredText(String msg) {
    System.out.println((char) 27 + "[31m" + msg);
    System.out.print((char) 27 + "[0m");
  }

  public static void blueColoredText(String msg) {
    System.out.println((char) 27 + "[36m" + msg);
    System.out.print((char) 27 + "[0m");
  }

}
