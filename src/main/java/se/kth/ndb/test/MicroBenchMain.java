package se.kth.ndb.test;


import com.mysql.clusterj.ClusterJHelper;
import com.mysql.clusterj.LockMode;
import com.mysql.clusterj.Session;
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
  private static String microBenchTypeStr = "";
  private static MicroBenchType microBenchType = null;

  @Option(name = "-nonDistributedPKOps", usage = "For each thread all the operations in a PK/BATCH test will be performed on a single partition")
  private static boolean nonDistributedPKOps = false;

  @Option(name = "-distributedPKOps", usage = "For each thread all the operations in the PK/BATCH test operations will go to different database partition")
  private static boolean distributedPKOps = false;

  @Option(name = "-schema", usage = "DB schemma name. Default is test")
  static private String schema = "test";

  @Option(name = "-dbHost", usage = "com.mysql.clusterj.connectstring.")
  static private String dbHost = "";

  @Option(name = "-rowsPerTx", usage = "Number of rows that are read in each transaction")
  static private int rowsPerTx = 1;

  @Option(name = "-bmDuration", usage = "For how long the bench mark should run. Time in ms")
  static private long benchmarkDuration = 10000;

  @Option(name = "-clientId", usage = "Id of this application. In case of distributed deployment each instance of this benchmark should have a unique id")
  static private int clientId = 0;

  @Option(name = "-createDummyData", usage = "Create dummy data")
  static private boolean createDummyData = false;

  @Option(name = "-skipPrompt", usage = "Do not ask for (Y/N) before starting the bench mark")
  static private boolean skipPrompt = false;

  @Option(name = "-runNumber", usage = "It is just a number that niquely identifies an expriemnt. This number is used in compiling the results. See compile.sh script")
  static private int runNumber = 1;

  @Option(name = "-numDummyRows", usage = "Number of dummy rows to create")
  static private int numDummyRows = 1000;

  @Option(name = "-updateData", usage = "Update Data")
  static private boolean updateData = false;

  private AtomicInteger successfulOps = new AtomicInteger(0);
  private AtomicInteger failedOps = new AtomicInteger(0);
  private static long lastOutput = 0;
  private SynchronizedDescriptiveStatistics latency = new SynchronizedDescriptiveStatistics();

  Random rand = new Random(System.currentTimeMillis());
  ExecutorService executor = null;
  SessionFactory sf = null;
  @Option(name = "-help", usage = "Print usages")
  private boolean help = false;

  private List workers = new ArrayList<Worker>();


  public void startApplication(String[] args) throws Exception {
    parseArgs(args);

    setUpDBConnection();

    System.out.println("Test type "+microBenchType);
    if(createDummyData) {
      writeDummyData();
    }

    createWorkers();

    writeData();

    if(!skipPrompt) {
      System.out.println("Press enter to start execution");
      System.in.read();
    }

    long startTime = System.currentTimeMillis();
    startMicroBench();
    long totExeTime = (System.currentTimeMillis()-startTime);

    long avgSpeed = (long)(((double)successfulOps.get()/(double)totExeTime)*1000);
    double avgLatency = latency.getMean()/1000000;

    String msg ="Results: "+ microBenchType+" NumThreads: "+numThreads+" Speed: "+avgSpeed+" ops/sec.\t\tAvg Op Latency: "+avgLatency;
    blueColoredText(msg);
    writeToFile("result.txt", true, msg);
    saveResultsToDB(avgSpeed, avgLatency);
    executor.shutdown();

  }

  void writeToFile(String filePath, boolean append, String msg) throws IOException {
    FileWriter out = new FileWriter(filePath, append);
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
        if ((distributedPKOps && nonDistributedPKOps) ||
                (!distributedPKOps && !nonDistributedPKOps)) {
          System.out.println("Seletect One. Distributed/Non Distributed batch Operations");
          showHelp(parser, true);
        }
        if(distributedPKOps) {
          microBenchType = MicroBenchType.PK_D;
        }else{
          microBenchType = MicroBenchType.PK_ND;
        }
      } else if (microBenchTypeStr.compareToIgnoreCase("BATCH") == 0) {
        if ((distributedPKOps && nonDistributedPKOps) ||
                (!distributedPKOps && !nonDistributedPKOps)) {
          System.out.println("Seletect One. Distributed/Non Distributed batch Operations");
          showHelp(parser, true);
        }

        if(distributedPKOps) {
          microBenchType = MicroBenchType.BATCH_D;
        }else{
          microBenchType = MicroBenchType.BATCH_ND;
        }
      } else if (microBenchTypeStr.compareToIgnoreCase(MicroBenchType.PPIS.toString()) == 0) {
        microBenchType = MicroBenchType.PPIS;
      } else if (microBenchTypeStr.compareToIgnoreCase(MicroBenchType.IS.toString()) == 0) {
        microBenchType = MicroBenchType.IS;
      } else if (microBenchTypeStr.compareToIgnoreCase(MicroBenchType.FTS.toString()) == 0) {
        microBenchType = MicroBenchType.FTS;
      } else {
        if(!createDummyData) {
          System.out.println("Wrong bench mark type");
          showHelp(parser, true);
        }
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
    props.setProperty("com.mysql.clusterj.connection.recv.thread.activation.threshold", "1");
    sf = ClusterJHelper.getSessionFactory(props);
  }

  public void writeDummyData() throws InterruptedException, IOException {
    AtomicInteger speed = new AtomicInteger(0);
    if(createDummyData){
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
    executor = Executors.newFixedThreadPool(numThreads);

    for (int i = 0; i < numThreads; i++) {
      Worker worker = new Worker(successfulOps, failedOps,
              benchmarkDuration, microBenchType, sf, rowsPerTx, distributedPKOps, lockMode,
              latency, updateData);
      workers.add(worker);
    }
  }


  public void writeData() throws Exception {
    for (Object worker  : workers){
      ((Worker)worker).writeData();
    }
  }

  public void startMicroBench() throws InterruptedException, IOException {
    executor.invokeAll(workers); //blocking call
  }


  protected void redColoredText(String msg) {
    System.out.println((char) 27 + "[31m" + msg);
    System.out.print((char) 27 + "[0m");
  }

  protected static void blueColoredText(String msg) {
    System.out.println((char) 27 + "[36m" + msg);
    System.out.print((char) 27 + "[0m");
  }

  private void saveResultsToDB(double avgSpeed, double avgLatency){
    Session session = sf.getSession();
    session.currentTransaction().begin();
    Results row = session.newInstance(Results.class);
    row.setTest(microBenchType.toString());
    row.setThreads(numThreads);
    row.setSpeed(avgSpeed);
    row.setLatency(avgLatency);
    row.setRun(runNumber);
    row.setBatchSize(rowsPerTx);
    row.setUpdateData(updateData?1:0);
    session.makePersistent(row);
    session.currentTransaction().commit();
  }
}
