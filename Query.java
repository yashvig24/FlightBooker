import java.io.FileInputStream;
import java.sql.*;
import java.util.*;
import java.lang.*;

/**
 * Runs queries against a back-end database
 */
public class Query
{
  private String configFilename;
  private Properties configProps = new Properties();

  private String jSQLDriver;
  private String jSQLUrl;
  private String jSQLUser;
  private String jSQLPassword;

  // DB Connection
  private Connection conn;

  // Logged In User
  private String username; // customer username is unique

  // Users search result
  private List<Itinerary> itineraries;

  private Map<Integer, Flight> map;;

  // Canned queries

  private static final String USER_VALUES = "SELECT u.password " +
                      "FROM users as u " +
                      "WHERE u.username = ?;";
  private PreparedStatement getUserValuesStatement;

  private static String INSERT_NEW_USER_TO_USERS = "INSERT INTO users VALUES (?, ?, ?);";
  private PreparedStatement insertUserToUser;

  private static String GET_DIRECT_FLIGHT_RESULT = "SELECT TOP(?) " + 
                                          "f.fid as fid, " +
                                          "f.day_of_month as day, " +
                                          "f.carrier_id as cid, " +
                                          "f.flight_num as flight_num, " +
                                          "f.origin_city as origin_city, " +
                                          "f.dest_city as dest_city, " +
                                          "f.actual_time as actual_time, " +
                                          "f.capacity as capacity, " +
                                          "f.price as price " + 
                                          "FROM flights as f " +
                                          "WHERE f.origin_city = ? and f.dest_city = ? and " +
                                          "f.day_of_month = ? and f.canceled = 0 " + 
                                          "ORDER BY actual_time, fid asc;";
  private static PreparedStatement getDirectFlightsSearchResult;

  private static String GET_INDIRECT_FLIGHT_RESULT = "SELECT TOP(?) " + 
                                          "f1.fid as fid1, " +
                                          "f2.fid as fid2, " +
                                          "f1.day_of_month as day1, " +
                                          "f2.day_of_month as day2, " +
                                          "f1.carrier_id as cid1, " +
                                          "f2.carrier_id as cid2, " +
                                          "f1.flight_num as flight_num1, " +
                                          "f2.flight_num as flight_num2, " +
                                          "f1.origin_city as origin_city1, " +
                                          "f2.origin_city as origin_city2, " +
                                          "f1.dest_city as dest_city1, " +
                                          "f2.dest_city as dest_city2, " +
                                          "f1.actual_time as actual_time1, " +
                                          "f2.actual_time as actual_time2, " +
                                          "f1.capacity as capacity1, " +
                                          "f2.capacity as capacity2, " +
                                          "f1.price as price1, " + 
                                          "f2.price as price2 " + 
                                          "FROM flights f1, flights f2 " +
                                          "WHERE f1.origin_city = ? and f2.dest_city = ? and " +
                                          "f1.dest_city = f2.origin_city and " +
                                          "f1.day_of_month = ? and f2.day_of_month = f1.day_of_month and " + 
                                          "f1.canceled = 0 and f2.canceled = 0 " + 
                                          "ORDER BY (f1.actual_time + f2.actual_time), f1.fid, f2.fid asc;";
  private PreparedStatement getIndirectFlightsSearchResult;

  // book

  private static final String GET_CURRENT_SEATS = "SELECT s.seats_left "+ 
                                                  "FROM seatChange as s " +
                                                  "WHERE s.fid = ?;";
  private PreparedStatement getCurrentSeatsStatement;
  
  private static final String UPDATE_SEATS_IN_FLIGHT = "UPDATE seatsRemaining " +
                                                       "SET seats_left = ? " + 
                                                       "WHERE fid = ?;";
  private PreparedStatement UpdateSeatsinFlight;

  private static final String BOOK_DIRECT_ITINERARY = "INSERT INTO reservations VALUES " +
                                                      "(?, NULL, ?, 0);";
  private PreparedStatement bookDirectItineraryStatement;

  private static final String BOOK_HOPPING_ITINERARY = "INSERT INTO reservations VALUES " +
                                                      "(?, ?, ?, 0);";
  private PreparedStatement bookHoppingItineraryStatement;

  private static final String GET_MOST_RECENT_RES_ID = "SELECT TOP(1) r.res_id FROM reservations r " +
                                                       "ORDER BY r.res_id desc;";
  private PreparedStatement getMostRecentReservationID;

  private static final String GET_FLIGHT_DATE = "SELECT f.day_of_month from flights f, itinerary i " +
                                                "WHERE f.fid = i.flight_one and i.it_id = ?;";
  private PreparedStatement getFlightDateStatement;

  private static final String CHECK_DATE_BOOKABLE = "WITH all_dates as ( " + 
                                                  "SELECT f.day_of_month as dates " +
                                                  "FROM flights f " +
                                                  "WHERE f.fid in ( " +
                                                  "SELECT r.fid1 FROM reservations as r " + 
                                                  "WHERE r.username = ?)) " +
                                                  "SELECT * FROM all_dates as ad " +
                                                  "where ad.dates = ?;";
  private PreparedStatement checkIfDateBookableStatement;

  private static final String GET_USER_RESERVATION = "SELECT * FROM reservations r " + 
                                                     "WHERE r.username = ?;";
  private PreparedStatement getUserReservationStatement;

  private static final String GET_FLIGHTS_IN_RESERVATION = "SELECT * FROM reservations r " +
                                                           "WHERE r.res_id = ?;";
  private PreparedStatement getFlightsFromReservations;

  // payment

  private static final String GET_RESERVATION_ID = "SELECT r.paid " +
                                                   "FROM reservations r " +
                                                   "WHERE r.username = ? and " +
                                                   "r.res_id = ?";
  private PreparedStatement getReservationIDStatement;

  private static final String GET_FIDS_FROM_RESID = "SELECT r.fid1 as fid1, " + 
                                                   "r.fid2 as fid2 " +
                                                   "FROM reservations as r " +
                                                   "WHERE r.res_id = ?;";
  private PreparedStatement getFidsFromResidStatement;

  // cancel

  private static final String ADD_SEATS_BACK = "UPDATE seatChange " +
                                               "SET seats_left = seats_left + 1 " + 
                                               "WHERE fid = ?;";
  private PreparedStatement addSeatsBack;

  private static final String DELETE_RESERVATIONS = "DELETE FROM reservations " +
                                               "WHERE res_id = ?;"; 
  private PreparedStatement deleteReservation;

  private static final String REFUND_MONEY = "UPDATE users " +
                                             "SET balance = balance + ? " + 
                                             "WHERE username = ?;";
  private PreparedStatement refundMoney;

  // transactions

  private static final String BEGIN_TRANSACTION_SQL = "SET TRANSACTION ISOLATION LEVEL SERIALIZABLE; BEGIN TRANSACTION;";
  private PreparedStatement beginTransactionStatement;

  private static final String COMMIT_SQL = "COMMIT TRANSACTION";
  private PreparedStatement commitTransactionStatement;

  private static final String ROLLBACK_SQL = "ROLLBACK TRANSACTION";
  private PreparedStatement rollbackTransactionStatement;

  class Flight
  {
    public int fid;
    public int dayOfMonth;
    public String carrierId;
    public String flightNum;
    public String originCity;
    public String destCity;
    public int time;
    public int capacity;
    public int price;

    @Override
    public String toString()
    {
      return "ID: " + fid + " Day: " + dayOfMonth + " Carrier: " + carrierId +
              " Number: " + flightNum + " Origin: " + originCity + " Dest: " + destCity + " Duration: " + time +
              " Capacity: " + capacity + " Price: " + price;
    }
  }

  class Itinerary implements Comparable<Itinerary> {
    public int it_id;
    public Flight flight_one;
    public Flight flight_two;

    @Override
    public String toString() {
      String it = "Itinerary " + it_id + ": " + numFlights() + " flight(s), " + duration() + " minutes\n";
      it += flight_one.toString();
      if(flight_two != null) {
        it += "\n" + flight_two.toString();
      }
      return it;
    }

    public int numFlights() {
      if(flight_two == null) {
        return 1;
      }
      else {
        return 2;
      }
    }

    public int duration() {
      int duration = flight_one.time;
      if(flight_two != null) {
        duration += flight_two.time;
      }
      return duration;
    }

    public int compareTo(Itinerary other) {
      return this.duration() - other.duration();
    }
  }

  public Query(String configFilename)
  {
    this.configFilename = configFilename;
  }

  /* Connection code to SQL Azure.  */
  public void openConnection() throws Exception
  {
    configProps.load(new FileInputStream(configFilename));

    jSQLDriver = configProps.getProperty("flightservice.jdbc_driver");
    jSQLUrl = configProps.getProperty("flightservice.url");
    jSQLUser = configProps.getProperty("flightservice.sqlazure_username");
    jSQLPassword = configProps.getProperty("flightservice.sqlazure_password");

    /* load jdbc drivers */
    Class.forName(jSQLDriver).newInstance();

    /* open connections to the flights database */
    conn = DriverManager.getConnection(jSQLUrl, // database
            jSQLUser, // user
            jSQLPassword); // password

    conn.setAutoCommit(true); //by default automatically commit after each statement
    conn.setTransactionIsolation(conn.TRANSACTION_SERIALIZABLE);
    /* You will also want to appropriately set the transaction's isolation level through:
       conn.setTransactionIsolation(...)
       See Connection class' JavaDoc for details.
    */
  }

  public void closeConnection() throws Exception
  {
    conn.close();
  }

  /**
   * Clear the data in any custom tables created. Do not drop any tables and do not
   * clear the flights table. You should clear any tables you use to store reservations
   * and reset the next reservation ID to be 1.
   */
  public void clearTables()
  {
    try {
      String query = "TRUNCATE TABLE reservations; delete from users; delete from seatChange;";
      conn.createStatement().executeUpdate(query);
    }
    catch(SQLException e) {
      e.printStackTrace();
    } 
  }

  /**
   * prepare all the SQL statements in this method.
   * "preparing" a statement is almost like compiling it.
   * Note that the parameters (with ?) are still not filled in
   */
  public void prepareStatements() throws Exception
  {
    getUserValuesStatement = conn.prepareStatement(USER_VALUES);
    insertUserToUser = conn.prepareStatement(INSERT_NEW_USER_TO_USERS);
    getDirectFlightsSearchResult = conn.prepareStatement(GET_DIRECT_FLIGHT_RESULT);
    getIndirectFlightsSearchResult = conn.prepareStatement(GET_INDIRECT_FLIGHT_RESULT);

    getCurrentSeatsStatement = conn.prepareStatement(GET_CURRENT_SEATS);
    bookDirectItineraryStatement = conn.prepareStatement(BOOK_DIRECT_ITINERARY);
    bookHoppingItineraryStatement = conn.prepareStatement(BOOK_HOPPING_ITINERARY);
    getMostRecentReservationID = conn.prepareStatement(GET_MOST_RECENT_RES_ID);
    checkIfDateBookableStatement = conn.prepareStatement(CHECK_DATE_BOOKABLE);
    getUserReservationStatement = conn.prepareStatement(GET_USER_RESERVATION);

    getReservationIDStatement = conn.prepareStatement(GET_RESERVATION_ID);
    getFidsFromResidStatement = conn.prepareStatement(GET_FIDS_FROM_RESID);
    getFlightsFromReservations = conn.prepareStatement(GET_FLIGHTS_IN_RESERVATION);

    addSeatsBack = conn.prepareStatement(ADD_SEATS_BACK);
    deleteReservation = conn.prepareStatement(DELETE_RESERVATIONS);
    refundMoney = conn.prepareStatement(REFUND_MONEY);

    beginTransactionStatement = conn.prepareStatement(BEGIN_TRANSACTION_SQL);
    commitTransactionStatement = conn.prepareStatement(COMMIT_SQL);
    rollbackTransactionStatement = conn.prepareStatement(ROLLBACK_SQL);

    /* add here more prepare statements for all the other queries you need */
    /* . . . . . . */
  }

  /**
   * Takes a user's username and password and attempts to log the user in.
   *
   * @param username
   * @param password
   *
   * @return If someone has already logged in, then return "User already logged in\n"
   * For all other errors, return "Login failed\n".
   *
   * Otherwise, return "Logged in as [username]\n".
   */
  public String transaction_login(String username, String password)
  {
    if(this.username != null) {
      return "User already logged in\n";
    }
    try {
      getUserValuesStatement.clearParameters();
      getUserValuesStatement.setString(1, username);
      ResultSet uservals = getUserValuesStatement.executeQuery();
      while(uservals.next()) {
        if(password.equals(uservals.getString(1))) 
          this.username = username;
        else 
          return "Login failed\n";
      }
      uservals.close();
    }
    catch(SQLException e) {
      return "Login failed\n";
    }
    return "Logged in as " + username + "\n";
  }

  /**
   * Implement the create user function.
   *
   * @param username new user's username. User names are unique the system.
   * @param password new user's password.
   * @param initAmount initial amount to deposit into the user's account, should be >= 0 (failure otherwise).
   *
   * @return either "Created user {@code username}\n" or "Failed to create user\n" if failed.
   */
  public String transaction_createCustomer(String username, String password, int initAmount)
  {

    if(initAmount < 0)
      return "Failed to create user\n";
    
    try {
      insertUserToUser.clearParameters();
      insertUserToUser.setString(1, username);
      insertUserToUser.setString(2, password);
      insertUserToUser.setInt(3, initAmount);
      insertUserToUser.executeUpdate();
    }
    catch(SQLException e) {
      return "Failed to create user\n";
    }
    return "Created user " + username + "\n";
  }

  private Flight fillInfoToFlights(String num, Flight flight, ResultSet indirflights) throws SQLException {
    flight.dayOfMonth = indirflights.getInt("day" + num);
    flight.fid = indirflights.getInt("fid" + num);
    flight.carrierId = indirflights.getString("cid" + num);
    flight.flightNum = indirflights.getString("flight_num" + num);
    flight.originCity = indirflights.getString("origin_city" + num);
    flight.destCity = indirflights.getString("dest_city" + num);
    flight.time = indirflights.getInt("actual_time" + num);
    flight.capacity = indirflights.getInt("capacity" + num);
    flight.price = indirflights.getInt("price" + num);
    this.map.put(indirflights.getInt("fid" + num), flight);
    return flight;
  }

  private void InsertIndirectFlightsToItinerary(String originCity, String destinationCity, 
                                    int dayOfMonth, int numberOfItineraries) throws SQLException, IllegalArgumentException {
    getIndirectFlightsSearchResult.clearParameters();
    getIndirectFlightsSearchResult.setInt(1, numberOfItineraries);
    getIndirectFlightsSearchResult.setString(2, originCity);
    getIndirectFlightsSearchResult.setString(3, destinationCity);
    getIndirectFlightsSearchResult.setInt(4, dayOfMonth);
    ResultSet indirflights = getIndirectFlightsSearchResult.executeQuery();
    int count = 0;
    while(indirflights.next()) {
      count++;
      Flight flight_one = new Flight();
      Flight flight_two = new Flight();
      flight_one = fillInfoToFlights("1", flight_one, indirflights);
      flight_two = fillInfoToFlights("2", flight_two, indirflights);
      Itinerary itn = new Itinerary();
      itn.flight_one = flight_one;
      itn.flight_two = flight_two;
      this.itineraries.add(itn);
    }
    if(count == 0) {
      throw new IllegalArgumentException();
    }
  }

  private int InsertDirectFlightsToItinerary(String originCity, String destinationCity,
                                           int dayOfMonth, int numberOfItineraries) 
                                           throws SQLException, IllegalArgumentException {
    getDirectFlightsSearchResult.clearParameters();
    getDirectFlightsSearchResult.setInt(1, numberOfItineraries);
    getDirectFlightsSearchResult.setString(2, originCity);
    getDirectFlightsSearchResult.setString(3, destinationCity);
    getDirectFlightsSearchResult.setInt(4, dayOfMonth);
    ResultSet dirflights = getDirectFlightsSearchResult.executeQuery();
    int count = 0;
    while(dirflights.next()) {
      Flight flight_one = new Flight();
      flight_one = fillInfoToFlights("", flight_one, dirflights);
      Itinerary itn = new Itinerary();
      itn.flight_one = flight_one;
      this.itineraries.add(itn);
      count++;
    }
    if(count == 0) {
      throw new IllegalArgumentException();
    }
    return count;
  }

  private void clearItinerary() {
    this.itineraries = new ArrayList<Itinerary>();
    this.map = new HashMap<Integer, Flight>();
  }

  /**
   * Implement the search function.
   *
   * Searches for flights from the given origin city to the given destination
   * city, on the given day of the month. If {@code directFlight} is true, it only
   * searches for direct flights, otherwise is searches for direct flights
   * and flights with two "hops." Only searches for up to the number of
   * itineraries given by {@code numberOfItineraries}.
   *
   * The results are sorted based on total flight time.
   *
   * @param originCity
   * @param destinationCity
   * @param directFlight if true, then only search for direct flights, otherwise include indirect flights as well
   * @param dayOfMonth
   * @param numberOfItineraries number of itineraries to return
   *
   * @return If no itineraries were found, return "No flights match your selection\n".
   * If an error occurs, then return "Failed to search\n".
   *
   * Otherwise, the sorted itineraries printed in the following format:
   *
   * Itinerary [itinerary number]: [number of flights] flight(s), [total flight time] minutes\n
   * [first flight in itinerary]\n
   * ...
   * [last flight in itinerary]\n
   *
   * Each flight should be printed using the same format as in the {@code Flight} class. Itinerary numbers
   * in each search should always start from 0 and increase by 1.
   *
   * @see Flight#toString()
   */
  public String transaction_search(String originCity, String destinationCity, boolean directFlight, int dayOfMonth,
                                   int numberOfItineraries)
  {
    return transaction_search_safe(originCity, destinationCity, directFlight, dayOfMonth, numberOfItineraries);
  }

  private String transaction_search_safe(String originCity, String destinationCity, 
                                    boolean directFlight, int dayOfMonth,
                                    int numberOfItineraries) {  
    clearItinerary();
    try {
      int numberOfItinerariesLeft = numberOfItineraries - InsertDirectFlightsToItinerary(originCity, 
          destinationCity, dayOfMonth, numberOfItineraries);
      if(!directFlight && numberOfItinerariesLeft > 0) {
        InsertIndirectFlightsToItinerary(originCity, destinationCity, dayOfMonth, 
          numberOfItinerariesLeft);
      }
    }
    catch(SQLException e) {
      return "Failed to search\n";
    }
    catch(IllegalArgumentException e) {
      return "No flights match your selection\n";
    }
    Collections.sort(itineraries);
    String all_it = "";
    for(int i = 0; i < this.itineraries.size(); i++) {
      Itinerary itn = this.itineraries.get(i);
      itn.it_id = i;
      all_it += itn.toString() + "\n";
    }
    return all_it;
  }

  private void decrementSeatsfromFlight(int fid) throws SQLException, IllegalStateException {
    getCurrentSeatsStatement.clearParameters();
    getCurrentSeatsStatement.setInt(1, fid);
    ResultSet numSeatsResult = getCurrentSeatsStatement.executeQuery();
    int numSeats = 0;
    if(!numSeatsResult.next()) {
      numSeats = getFlightfromFlightID(fid).capacity;
      String insertToseatChange = "INSERT INTO seatChange VALUES ("+fid + ", " + numSeats + ");";
      conn.createStatement().executeUpdate(insertToseatChange);
    }
    else {
      numSeats = numSeatsResult.getInt(1);
    }
    numSeatsResult.close();
    if(numSeats == 0) {
      throw new IllegalStateException();
    }
    int updatedSeats = numSeats - 1;
    String updateSeatsQuery = "UPDATE seatChange " +
                              "SET seats_left = " + updatedSeats + 
                              "WHERE fid = " + fid + ";";
    conn.createStatement().executeUpdate(updateSeatsQuery);
  }

  // books flight with flight id fid
  // returns reservation id
  private int bookFlight(int fid) throws SQLException {
    bookDirectItineraryStatement.clearParameters();
    bookDirectItineraryStatement.setString(2, this.username);
    bookDirectItineraryStatement.setInt(1, fid);
    bookDirectItineraryStatement.executeUpdate();
    ResultSet recentResID = getMostRecentReservationID.executeQuery();
    recentResID.next();
    return recentResID.getInt(1);
  }

  // books flights with flight id fid1, fid2
  // returns single reservation id
  private int bookFlight(int fid1, int fid2) throws SQLException {
    bookHoppingItineraryStatement.clearParameters();
    bookHoppingItineraryStatement.setString(3, this.username);
    bookHoppingItineraryStatement.setInt(1, fid1);
    bookHoppingItineraryStatement.setInt(2, fid2);
    bookHoppingItineraryStatement.executeUpdate();
    ResultSet recentResID = getMostRecentReservationID.executeQuery();
    recentResID.next();
    return recentResID.getInt(1);
  }

  private boolean hasReservationOnSameDay(int flightDate) {
    try {
      checkIfDateBookableStatement.clearParameters();
      checkIfDateBookableStatement.setString(1, this.username);
      checkIfDateBookableStatement.setInt(2, flightDate);
      return checkIfDateBookableStatement.executeQuery().next();
    }
    catch(SQLException e) {
      e.printStackTrace();
    }
    return true;
  }

  private Itinerary getItineraryFromItinaryId(int it_id) {
    for(int i = 0; i < itineraries.size(); i++) {
      Itinerary itn = this.itineraries.get(i);
      if(itn.it_id == it_id)
        return itn;
    }
    return null;
  }

  /**
   * Implements the book itinerary function.
   *
   * @param itineraryId ID of the itinerary to book. This must be one that is returned by search in the current session.
   *
   * @return If the user is not logged in, then returns "Cannot book reservations, not logged in\n".
   * If try to book an itinerary with invalid ID, then returns "No such itinerary {@code itineraryId}\n".
   * If the user already has a reservation on the same day as the one that they are trying to book now, then returns
   * "You cannot book two flights in the same day\n".
   * For all other errors, return "Booking failed\n".
   *
   * And if booking succeeded, returns "Booked flight(s), reservation ID: [reservationId]\n" where
   * reservationId is a unique number in the reservation system that starts from 1 and increments by 1 each time a
   * successful reservation is made by any user in the system.
   */
  public String transaction_book(int itineraryId)
  {
    if(this.username == null) {
      return "Cannot book reservations, not logged in\n";
    }
    Itinerary itn = getItineraryFromItinaryId(itineraryId);
    if(itn == null) {
      return "No such itinerary " + itineraryId + "\n";
    }
    if(hasReservationOnSameDay(itn.flight_one.dayOfMonth)) {
      return "You cannot book two flights in the same day\n";
    }
    int res_id = 0;
    beginTransaction();
    try {
      int fid1 = itn.flight_one.fid;
      decrementSeatsfromFlight(fid1);
      if(itn.flight_two != null) {
        int fid2 = itn.flight_two.fid;
        decrementSeatsfromFlight(fid2);
        res_id = bookFlight(fid1, fid2);
      }
      else {
        res_id = bookFlight(fid1);
      }
    }
    catch(SQLException e) {
      rollbackTransaction();
      return "Booking failed\n";
    }
    catch(IllegalStateException e) {
      rollbackTransaction();
      return "Booking failed\n";
    }
    commitTransaction();
    return "Booked flight(s), reservation ID: " + res_id + "\n";
  }

  private ResultSet getUserReservation() throws SQLException {
    getUserReservationStatement.clearParameters();
    getUserReservationStatement.setString(1, this.username);
    return getUserReservationStatement.executeQuery();
  }

  /**
   * Implements the reservations function.
   *
   * @return If no user has logged in, then return "Cannot view reservations, not logged in\n"
   * If the user has no reservations, then return "No reservations found\n"
   * For all other errors, return "Failed to retrieve reservations\n"
   *
   * Otherwise return the reservations in the following format:
   *
   * Reservation [reservation ID] paid: [true or false]:\n"
   * [flight 1 under the reservation]
   * [flight 2 under the reservation]
   * Reservation [reservation ID] paid: [true or false]:\n"
   * [flight 1 under the reservation]
   * [flight 2 under the reservation]
   * ...
   *
   * Each flight should be printed using the same format as in the {@code Flight} class.
   *
   * @see Flight#toString()
   */
  public String transaction_reservations()
  {
    if(this.username == null) {
      return "Cannot view reservations, not logged in\n";
    }
    String result = "";
    beginTransaction();
    try {
      ResultSet reservations = getUserReservation();
      boolean hasRes = false;
      while(reservations.next()) {
        result += "Reservation ";
        hasRes = true;
        int paid = reservations.getInt(5);
        result += reservations.getInt(1) + " paid: " + (paid == 1) + ":\n";
        int fid1 = reservations.getInt(2);
        Flight flight_one = getFlightfromFlightIDTable(fid1);
        result += flight_one.toString();
        Object maybe_null_fid = reservations.getObject(3);
        if(maybe_null_fid != null) {
          int fid2 = (Integer) maybe_null_fid;
          Flight flight_two = getFlightfromFlightIDTable(fid2);
          result += flight_two.toString();
        }
        commitTransaction();
      }
      if(!hasRes) {
        rollbackTransaction();
        return "No reservations found\n";
      }
    }
    catch(SQLException e) {
      rollbackTransaction();
      e.printStackTrace();
      return "Failed to retrieve reservations\n";
    }
    return (result + "\n");
  }

  private Flight getFlightfromFlightID(int fid) {
    if(this.map.containsKey(fid))
      return this.map.get(fid);
    return null;
  }

  private Flight getFlightfromFlightIDTable(int fid) throws SQLException {
    String query = "Select * from flights where fid = " + fid;
    ResultSet result = conn.createStatement().executeQuery(query);
    boolean pass = false;
    Flight flight = new Flight();
    while(result.next()) {
      pass = true;
      flight.dayOfMonth = result.getInt("day_of_month");
      flight.fid = result.getInt("fid");
      flight.carrierId = result.getString("carrier_id");
      flight.flightNum = result.getString("flight_num");
      flight.originCity = result.getString("origin_city");
      flight.destCity = result.getString("dest_city");
      flight.time = result.getInt("actual_time");
      flight.capacity = result.getInt("capacity");
      flight.price = result.getInt("price");
    }
    if(!pass) {
      throw new SQLException();
    }
    return flight;
  }

  /**
   * Implements the cancel operation.
   *
   * @param reservationId the reservation ID to cancel
   *
   * @return If no user has logged in, then return "Cannot cancel reservations, not logged in\n"
   * For all other errors, return "Failed to cancel reservation [reservationId]"
   *
   * If successful, return "Canceled reservation [reservationId]"
   *
   * Even though a reservation has been canceled, its ID should not be reused by the system.
   */
  public String transaction_cancel(int reservationId)
  {
    // only implement this if you are interested in earning extra credit for the HW!
    if(this.username == null) {
      return "Cannot cancel reservation, not logged in\n";
    }
    beginTransaction();
    try {
      getFlightsFromReservations.clearParameters();
      getFlightsFromReservations.setInt(1, reservationId);
      ResultSet flightsInRes = getFlightsFromReservations.executeQuery();
      if(flightsInRes.next()) {
        int totalPrice = 0;
        int fid1 = flightsInRes.getInt(2);
        Object maybe_null_fid = flightsInRes.getObject(3);
        int paid = flightsInRes.getInt(5);
        Flight flight_one = getFlightfromFlightIDTable(fid1);
        totalPrice += flight_one.price;
        addSeatsBack.clearParameters();
        addSeatsBack.setInt(1, flight_one.fid);
        addSeatsBack.executeUpdate();
        if(maybe_null_fid != null) {
          int fid2 = (Integer) maybe_null_fid;
          Flight flight_two = getFlightfromFlightIDTable(fid2);
          totalPrice += flight_two.price;
          addSeatsBack.clearParameters();
          addSeatsBack.setInt(1, flight_two.fid);
          addSeatsBack.executeUpdate();
        }
        deleteReservation.clearParameters();
        deleteReservation.setInt(1, reservationId);
        deleteReservation.executeUpdate();
        if(paid == 1) {
          refundMoney.clearParameters();
          refundMoney.setInt(1, totalPrice);
          refundMoney.setString(2, this.username);
          refundMoney.executeUpdate();
        }
      }
      else {
        rollbackTransaction();
        System.out.println("noflightinres");
        return ("Failed to cancel reservation " + reservationId + "\n");
      }
    }
    catch(SQLException e) {
      rollbackTransaction();
      System.out.println("exception");
      e.printStackTrace();
      return ("Failed to cancel reservation " + reservationId + "\n");
    }
    commitTransaction();
    return ("Canceled reservation " + reservationId + "\n");
  }

  private int[] checkEnoughBalance(int reservationId) throws SQLException {
    getFidsFromResidStatement.clearParameters();
    getFidsFromResidStatement.setInt(1, reservationId);
    ResultSet priceSet = getFidsFromResidStatement.executeQuery();
    priceSet.next();
    int fid1 = priceSet.getInt(1);
    Object maybe_null_fid = priceSet.getObject(2);
    int price = getFlightfromFlightID(fid1).price;
    if(maybe_null_fid != null) {
      int fid2 = (Integer) maybe_null_fid;
      price += getFlightfromFlightID(fid2).price;
    }
    String query = "SELECT u.balance FROM users u WHERE u.username = '" + this.username + "';";
    ResultSet userBalance = conn.createStatement().executeQuery(query);
    userBalance.next();
    int bal = userBalance.getInt(1);
    return new int[]{bal,price};
  }

  private void executePay(int deduction, int res_id) throws SQLException {
    Statement stmt = conn.createStatement();
    String query = "UPDATE users " + 
                   "SET balance = balance - " + deduction + 
                   "WHERE username = '" + this.username + "';" + 
                   "UPDATE reservations " + 
                   "SET paid = 1 WHERE username = '" + this.username +
                   "' and res_id = " + res_id + ";";
    stmt.executeUpdate(query);
  }

  /**
   * Implements the pay function.
   *
   * @param reservationId the reservation to pay for.
   *
   * @return If no user has logged in, then return "Cannot pay, not logged in\n"
   * If the reservation is not found / not under the logged in user's name, then return
   * "Cannot find unpaid reservation [reservationId] under user: [username]\n"
   * If the user does not have enough money in their account, then return
   * "User has only [balance] in account but itinerary costs [cost]\n"
   * For all other errors, return "Failed to pay for reservation [reservationId]\n"
   *
   * If successful, return "Paid reservation: [reservationId] remaining balance: [balance]\n"
   * where [balance] is the remaining balance in the user's account.
   */
  public String transaction_pay (int reservationId)
  {
    if(this.username == null) {
      return "Cannot pay, not logged in\n";
    }
    beginTransaction();
    try {
      getReservationIDStatement.clearParameters();
      getReservationIDStatement.setString(1, this.username);
      getReservationIDStatement.setInt(2, reservationId);
      ResultSet res = getReservationIDStatement.executeQuery();
      if(!res.next() || res.getInt(1) == 1) {
        rollbackTransaction();
        return "Cannot find unpaid reservation " + reservationId + " under user: " + this.username + "\n";
      }
      int[] amounts = checkEnoughBalance(reservationId);
      if(amounts[0] >= amounts[1]) {
        executePay(amounts[1], reservationId);
      }
      else {
        rollbackTransaction();
        return "User has only " + amounts[0] + " in account but itinerary costs " + amounts[1] + "\n";
      }
      commitTransaction();
      int balance = amounts[0] - amounts[1];
      return "Paid reservation: " + reservationId + " remaining balance: " + balance + "\n";
    }
    catch(SQLException e) {
      rollbackTransaction();
      return "Failed to pay for reservation " + reservationId + "\n";
    }
  }

  /* some utility functions below */

  public void beginTransaction()
  {
    try {
      conn.setAutoCommit(false);
      beginTransactionStatement.executeUpdate();
    }
    catch(SQLException e) {
      e.printStackTrace();
    }
  }

  public void commitTransaction()
  {
    try {
      commitTransactionStatement.executeUpdate();
      conn.setAutoCommit(true);
    }
    catch(SQLException e) {
      e.printStackTrace();
    }
  }

  public void rollbackTransaction()
  {
    try {
      rollbackTransactionStatement.executeUpdate();
      conn.setAutoCommit(true);
    }
    catch(SQLException e) {
      e.printStackTrace();
    }
  }
} 