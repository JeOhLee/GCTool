package edu.kaist.algo.api;

import com.google.common.annotations.VisibleForTesting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * The Ticketer class manages various information for GC log analysis.
 * It uses Jedis, a java redis connection library, to manage the data.
 * Avaliable resources are : analysis status, name of logfile, name of metadata file, and
 * name of analysis result file.
 *
 * <p>When the Garbage Collection log file is uploaded to server for analysis request,
 * the server will issue an identification called "ticket" for the client to identify
 * the specific log file stored within the server. The client can request various information
 * such as benchmark test time (stored in metadata file) or statistical analysis result
 * (stored in result file), based on the ticket number it has.
 */
public class Ticketer {
  private static final int DEFAULT_REDIS_PORT = 6379;
  private static final String LOCALHOST = "localhost";
  private static final String COUNTER = "counter";
  private static final String TICKET = "ticket";

  static final String STATUS = "status";
  static final String LOGFILE = "logfile";
  static final String META = "meta";
  static final String RESULT = "result";

  private final JedisPool jedisPool;
  private final Logger logger = LoggerFactory.getLogger(Ticketer.class);

  /**
   * Status enum class that represents the current status of GC analysis.
   * Since redis uses only Strings to store the values, enum Satus class will
   * make it easy to safely convert get() results into status and vice versa.
   *
   * <p>NOT_READY : the file to be analyzed is not ready - ticket default value
   * COMPLETED : analysis is complete
   * ANALYZING : in analysis process
   * ERROR : error occurred during analysis
   */
  public enum Status { NOT_READY, COMPLETED, ANALYZING, ERROR }

  /**
   * Creates a Ticketer instance.
   *
   * @param host jedis server host
   * @param port jedis port
   */
  public Ticketer(String host, int port) {
    this(new JedisPool(new JedisPoolConfig(), host, port));
  }

  /**
   * Creates a Ticketer instance with default port number 6379.
   *
   * @param host jedis server host
   */
  public Ticketer(String host) {
    this(host, DEFAULT_REDIS_PORT);
  }

  /**
   * Creates a Ticketer instance given JedisPool class instance.
   *
   * @param jedisPool JedisPool instance for jedis use in this class.
   */
  Ticketer(JedisPool jedisPool) {
    this.jedisPool = jedisPool;
  }

  /**
   * Issues a ticket.
   * The ticket number is incremented by 1 whenever it is issued.
   *
   * @return ticket number
   */
  public long issueTicket() {
    try (Jedis jedis = jedisPool.getResource()) {
      return jedis.incr(COUNTER);
    }
  }

  /**
   * Creates a 'key' string that is used for redis(jedis) value setting.
   *
   * <p>It has a format of ticket:TICKET_NUMBER:RESOURCE_NAME.
   * For example, if the ticket number is 2 and we want the result file name,
   * then the key will be : "ticket:2:result"
   *
   * <p>The resourceName argument SHOULD be either one of :
   * Ticketer.LOGFILE, Ticketer.STATUS, Ticketer.META, Ticketer.RESULT.
   *
   * @param ticketNum the ticket number
   * @param resourceName the string name of the resource
   * @return key string for jedis use
   */
  @VisibleForTesting
  static String makeKey(long ticketNum, String resourceName)
      throws IllegalArgumentException {

    // checks the number of ticket
    if (ticketNum <= 0) {
      throw new IllegalArgumentException("Invalid number : ticket number should be > 0");
    }

    // checks the validity of resource name
    if (!resourceName.equals(STATUS) && !resourceName.equals(LOGFILE)
        && !resourceName.equals(META) && !resourceName.equals(RESULT)) {
      throw new IllegalArgumentException("Invalid resource name.");
    }

    return new StringBuilder(TICKET).append(":")
        .append(ticketNum).append(":")
        .append(resourceName).toString();
  }

  /**
   * Gives the current status of GC analysis, given the ticket number.
   *
   * <p>The status is either NOT_READY, ERROR, COMPLETED, and ANALYZING.
   *
   * @param ticketNum the ticket number
   * @return enum status of current GC log analysis status
   */
  public Status getStatus(long ticketNum) {
    try (Jedis jedis = jedisPool.getResource()) {
      String key = makeKey(ticketNum, STATUS);
      String statusString = jedis.get(key);
      if (statusString == null) {
        return null;
      }
      return Status.valueOf(statusString);
    }
  }

  /**
   * Sets the status of GC analysis.
   *
   * <p>The status is either NOT_READY, ERROR, COMPLETED, and ANALYZING.
   *
   * @param ticketNum the ticket number
   * @param status the enum Status to be set
   */
  public void setStatus(long ticketNum, Status status) {
    try (Jedis jedis = jedisPool.getResource()) {
      String key = makeKey(ticketNum, STATUS);
      jedis.set(key, status.name());
    }
  }

  /**
   * Gives the name of the log file (original GC log data).
   *
   * @param ticketNum the ticket number
   * @return the name of the log file
   */
  public String getLogFile(long ticketNum) {
    try (Jedis jedis = jedisPool.getResource()) {
      String key = makeKey(ticketNum, LOGFILE);
      return jedis.get(key);
    }
  }

  /**
   * Sets the name of the log file (original GC log data).
   *
   * @param ticketNum the ticket number
   * @param logfile the name of the log file
   */
  public void setLogFile(long ticketNum, String logfile) {
    try (Jedis jedis = jedisPool.getResource()) {
      String key = makeKey(ticketNum, LOGFILE);
      jedis.set(key, logfile);
    }
  }

  /**
   * Gives the name of metadata information file.
   *
   * @param ticketNum the ticket number
   * @return the name of the metadata information file
   */
  public String getMeta(long ticketNum) {
    try (Jedis jedis = jedisPool.getResource()) {
      String key = makeKey(ticketNum, META);
      return jedis.get(key);
    }
  }

  /**
   * Sets the name of metadata information file.
   *
   * @param ticketNum the ticket number
   * @param meta the name of metadata information file
   */
  public void setMeta(long ticketNum, String meta) {
    try (Jedis jedis = jedisPool.getResource()) {
      String key = makeKey(ticketNum, META);
      jedis.set(key, meta);
    }
  }

  /**
   * Gives the name of result file where GC analysis information is stored.
   *
   * @param ticketNum the ticket number
   * @return the name of result file
   */
  public String getResult(long ticketNum) {
    try (Jedis jedis = jedisPool.getResource()) {
      String key = makeKey(ticketNum, RESULT);
      return jedis.get(key);
    }
  }

  /**
   * Sets the name of result file where GC analysis information is stored.
   *
   * @param ticketNum the ticket number
   * @param result the name of result file
   */
  public void setResult(long ticketNum, String result) {
    try (Jedis jedis = jedisPool.getResource()) {
      String key = makeKey(ticketNum, RESULT);
      jedis.set(key, result);
    }
  }

  /**
   * Deletes the information of the ticket entirely.
   *
   * @param ticketNum the ticket number to delete
   */
  public void deleteResource(long ticketNum) {
    try (Jedis jedis = jedisPool.getResource()) {
      jedis.del(makeKey(ticketNum, STATUS));
      jedis.del(makeKey(ticketNum, RESULT));
      jedis.del(makeKey(ticketNum, LOGFILE));
      jedis.del(makeKey(ticketNum, META));
    }
  }

  /**
   * Close the Jedis pool application.
   */
  public void closeTicketer() {
    jedisPool.destroy();
  }
}
