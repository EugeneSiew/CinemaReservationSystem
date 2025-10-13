import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

// Theatre class representing a theatre with a fixed number of seats
class Theatre {
    // Initialize the list of seats, the map of seat to customer, the list of seat locks, and the total number of seats
    private final List<Boolean> seats;
    private final Map<Integer, Integer> seatToCustomerMap;
    private final List<Lock> seatLocks;
    private final int totalSeats;

    // Constructor to initialize the theatre with the specified number of seats
    public Theatre(int numSeats) {
        seats = new ArrayList<>(Collections.nCopies(numSeats, true)); // Initializes all seats to available (true)
        seatToCustomerMap = new HashMap<>();
        seatLocks = new ArrayList<>();
        for (int i = 0; i < numSeats; i++) {
            seatLocks.add(new ReentrantLock());
        }
        totalSeats = numSeats;
    }

    // Method to attempt to lock the specified seats
    public boolean attemptLockSeats(List<Integer> seatNumbers) {
        // Sort seat numbers to ensure consistent lock ordering and avoid deadlocks
        Collections.sort(seatNumbers);

        // Attempt to lock all seats
        for (int seatNumber : seatNumbers) {
            seatLocks.get(seatNumber - 1).lock(); // Adjust for 1-based indexing
        }

        // Check if all seats are available
        boolean allAvailable = true;
        for (int seatNumber : seatNumbers) {
            if (!seats.get(seatNumber - 1)) { // Adjust for 1-based indexing
                allAvailable = false;
                break;
            }
        }

        // If not all seats are available, release locks
        if (!allAvailable) {
            releaseLockSeats(seatNumbers);
        }
        return allAvailable;
    }

    // Method to get the list of unavailable seats
    public List<Integer> getUnavailableSeats(List<Integer> seatNumbers) {
        List<Integer> unavailableSeats = new ArrayList<>();
        for (int seatNumber : seatNumbers) {
            // Check if the seat is unavailable
            if (!seats.get(seatNumber - 1)) { // Adjust for 1-based indexing
                unavailableSeats.add(seatNumber);
            }
        }
        return unavailableSeats;
    }

    // Method to release the locks on the specified seats
    public void releaseLockSeats(List<Integer> seatNumbers) {
        for (int seatNumber : seatNumbers) {
            seatLocks.get(seatNumber - 1).unlock(); // Release the lock on the seats
        }
    }

    // Method to confirm the reservation of the specified seats for the given customer
    public boolean confirmSeats(List<Integer> seatNumbers, int customerId) {
        // Check if all seats are still available
        boolean allAvailable = true;
        for (int seatNumber : seatNumbers) {
            if (!seats.get(seatNumber - 1)) { // Adjust for 1-based indexing
                allAvailable = false;
                break;
            }
        }

        // If all seats are available, confirm the reservation
        if (allAvailable) {
            for (int seatNumber : seatNumbers) {
                seats.set(seatNumber - 1, false); // Mark the seats as reserved
                seatToCustomerMap.put(seatNumber, customerId); // Map the seat to the customer
            }
        }

        // Release the locks after confirmation attempt
        releaseLockSeats(seatNumbers);
        return allAvailable; // Successfully reserved the seats
    }

    // Method to get the list of available seats
    public List<Integer> getAvailableSeats() {
        List<Integer> availableSeats = new ArrayList<>();
        for (int i = 0; i < seats.size(); i++) {
            if (seats.get(i)) {
                availableSeats.add(i + 1); // Adjust for 1-based indexing
            }
        }
        return availableSeats; // Returns the list of available seats
    }

    // Method to get the map of seat to customer
    public Map<Integer, Integer> getSeatToCustomerMap() {
        return new HashMap<>(seatToCustomerMap);
    }

    // Method to get the count of booked seats
    public int getBookedSeatsCount() {
        int count = 0;
        // Check if the seat is booked
        for (Boolean seat : seats) {
            // Increment the count if the seat is booked (seat == false)
            if (!seat) {
                count++;
            }
        }
        return count;
    }

    // Method to get the total number of seats
    public int getTotalSeats() {
        return totalSeats;
    }
}

// ReservationSite class to handle ticket reservations
class ReservationSite {
    // Initialize the list of theatres, the map of customer to seat count, and the map of customer to seats
    private final List<Theatre> theatres;
    private final Map<Integer, Integer> customerSeatCountMap;
    private final Map<Integer, List<Integer>> customerSeatsMap;

    // Constructor to initialize the reservation site with the specified list of theatres
    public ReservationSite(List<Theatre> theatres) {
        this.theatres = theatres;
        this.customerSeatCountMap = new ConcurrentHashMap<>();
        this.customerSeatsMap = new ConcurrentHashMap<>();
    }

    // Method to attempt to reserve seats for the specified customer
    public void attemptReservation(int customerId) {
        // Variable to keep track if all theatres are full
        boolean allTheatresFull = false;
        Random random = new Random();

        // Keep attempting to reserve seats until all theatres are full
        while (!allTheatresFull) {
            allTheatresFull = true;
            for (Theatre theatre : theatres) {
                // Check if any theatre has available seats
                if (!theatre.getAvailableSeats().isEmpty()) {
                    // If any theatre has available seats, set the flag to false and break
                    allTheatresFull = false;
                    break;
                }
            }
            if (allTheatresFull) {
                // If all theatres are full, print a message and return, exiting the loop
                System.out.println("Customer " + customerId + " could not find any available seats in any theatre.");
                return;
            }

            // Randomly selects a theatre
            int theatreId = random.nextInt(theatres.size());
            Theatre theatre = theatres.get(theatreId);
            // Randomly decides to book 1 to 3 seats
            int numSeatsToBook = random.nextInt(3) + 1; 
            List<Integer> availableSeats = theatre.getAvailableSeats();

            // Check if there are enough seats available to book
            if (availableSeats.size() >= numSeatsToBook) {
                List<Integer> seatsToBook = new ArrayList<>();
                // Randomly select seats to book based on the number of seats to book
                for (int i = 0; i < numSeatsToBook; i++) {
                    seatsToBook.add(availableSeats.remove(random.nextInt(availableSeats.size())));
                }
                System.out.println("Customer " + customerId + " is attempting to book seats " + seatsToBook + " in Theatre " + (theatreId + 1));

                // Attempt to lock the selected seats
                if (theatre.attemptLockSeats(seatsToBook)) {
                    try {
                        // Simulates delay before confirming the reservation
                        Thread.sleep(random.nextInt(501) + 500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    // Confirm the reservation of the selected seats
                    if (theatre.confirmSeats(seatsToBook, customerId)) {
                        // Update the customer seat count and customer seats map
                        synchronized (customerSeatCountMap) {
                            customerSeatCountMap.put(customerId, seatsToBook.size());
                        }
                        synchronized (customerSeatsMap) {
                            customerSeatsMap.computeIfAbsent(customerId, k -> new ArrayList<>()).addAll(seatsToBook);
                        }
                        System.out.println("Customer " + customerId + " successfully reserved seats " + seatsToBook + " in Theatre " + (theatreId + 1));
                        return;  // Successful reservation
                    } 
                    else{
                        // If the seats are no longer available, print a message and attempt to find other seats
                        List<Integer> unavailableSeats = theatre.getUnavailableSeats(seatsToBook);
                        System.out.println("Customer " + customerId + " failed to reserve seats " + seatsToBook + " in Theatre " + (theatreId + 1) + " because seats " + unavailableSeats + " were already reserved. Attempting to find other seats.");    
                    }
                } else {
                    // If the seats are no longer available, print a message and attempt to find other seats
                    List<Integer> unavailableSeats = theatre.getUnavailableSeats(seatsToBook);
                    System.out.println("Customer " + customerId + " failed to reserve seats " + seatsToBook + " in Theatre " + (theatreId + 1) + " because seats " + unavailableSeats + " were already reserved. Attempting to find other seats.");
                }
            } else {
                // If there are not enough seats available, print a message and attempt to find other seats
                System.out.println("Customer " + customerId + " could not find enough seats to book " + numSeatsToBook + " seats in Theatre " + (theatreId + 1));
            }
        }
    }

    // Method to get the map of customer to seat count
    public Map<Integer, Integer> getCustomerSeatCountMap() {
        return customerSeatCountMap;
    }

    // Method to get the map of customer to seats
    public Map<Integer, List<Integer>> getCustomerSeatsMap() {
        return customerSeatsMap;
    }

    // Method to print the customer seat assignments for each theatre
    public void printCustomerSeatAssignments() {
        // Iterate over each theatre
        for (int i = 0; i < theatres.size(); i++) {
            System.out.println("\n");
            Theatre theatre = theatres.get(i); // Get the current theatre
            System.out.println("Theatre " + (i + 1) + " customer seat assignments:"); // Print the theatre number
            
            // Create a map to store the customer to their reserved seats in the current theatre
            Map<Integer, List<Integer>> theatreCustomerSeatsMap = new HashMap<>();
            
            // Iterate over the customer seats map to find the seats reserved in the current theatre
            for (Map.Entry<Integer, List<Integer>> entry : customerSeatsMap.entrySet()) {
                int customerId = entry.getKey(); // Get the customer ID
                List<Integer> seats = entry.getValue(); // Get the list of seats reserved by this customer
                List<Integer> theatreSeats = new ArrayList<>(); // Create a list to store the seats reserved in this theatre
                
                // Iterate over the seats reserved by the customer
                for (Integer seat : seats) {
                    // Check if the seat is reserved by the current customer in the current theatre
                    if (theatre.getSeatToCustomerMap().get(seat) != null && theatre.getSeatToCustomerMap().get(seat) == customerId) {
                        theatreSeats.add(seat); // Add the seat to the list of seats reserved in this theatre
                    }
                }
                
                // Add the customer and their reserved seats to the theatre customer seats map if they have reserved any seats in this theatre
                if (!theatreSeats.isEmpty()) {
                    theatreCustomerSeatsMap.put(customerId, theatreSeats);
                }
            }
            
            // Iterate over the map of customers and their reserved seats in the current theatre
            for (Map.Entry<Integer, List<Integer>> entry : theatreCustomerSeatsMap.entrySet()) {
                int customerId = entry.getKey(); // Get the customer ID
                List<Integer> theatreSeats = entry.getValue(); // Get the list of seats reserved by this customer in this theatre
                System.out.println("Customer " + customerId + " reserved " + theatreSeats.size() + " seats: " + theatreSeats); // Print the customer ID and their reserved seats
            }
            
            int bookedSeats = theatre.getBookedSeatsCount(); // Get the number of seats booked in the current theatre
            System.out.println("Total seats booked in Theatre " + (i + 1) + ": " + bookedSeats + "/" + theatre.getTotalSeats()); // Print the total number of seats booked in the theatre
        }
    }
}

// Customer class implementing a Callable task representing a customer attempting to reserve seats
class Customer implements Callable<Void> {
    private final int customerId;
    // Reference to the ReservationSite instance managing seat reservations
    private final ReservationSite reservationSite;

    // Constructor to initialize Customer with a customerId and a reference to the ReservationSite
    public Customer(int customerId, ReservationSite reservationSite) {
        this.customerId = customerId;
        this.reservationSite = reservationSite;
    }

    // The call method is executed when the customer attempts to reserve seats
    @Override
    public Void call() {
        // Attempt to reserve seats for this customer by calling the attemptReservation method
        reservationSite.attemptReservation(customerId);
        // Return null since the Callable<Void> interface requires a return type of Void
        return null;
    }
}

// Driver class to run the cinema reservation system
public class CinemaReservationSystem {
    // Number of theaters in the cinema
    private static final int NUM_THEATERS = 3;
    // Number of seats in each theater
    private static final int SEATS_PER_THEATER = 20;
    // Number of customers trying to reserve seats
    private static final int NUM_CUSTOMERS = 100;

    public static void main(String[] args) {
        // List to hold theater instances
        List<Theatre> theatres = new ArrayList<>();
        // Initialize theaters with a fixed number of seats
        for (int i = 0; i < NUM_THEATERS; i++) {
            theatres.add(new Theatre(SEATS_PER_THEATER));
        }

        // Create an instance of ReservationSite with the list of theaters
        ReservationSite reservationSite = new ReservationSite(theatres);

        // Create a thread pool with a fixed number of threads equal to the number of customers
        ExecutorService executorService = Executors.newFixedThreadPool(NUM_CUSTOMERS);
        // Create a CompletionService to manage the completion of customer tasks
        CompletionService<Void> completionService = new ExecutorCompletionService<>(executorService);

        // Submit customer tasks to the completion service
        for (int i = 1; i <= NUM_CUSTOMERS; i++) {
            completionService.submit(new Customer(i, reservationSite));
        }

        // Wait for all customers to complete their reservations
        for (int i = 0; i < NUM_CUSTOMERS; i++) {
            try {
                // Block until a task is completed
                Future<Void> future = completionService.take();
                // Retrieve the result to handle potential exceptions
                future.get();
            } catch (InterruptedException e) {
                // Handle thread interruption
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                // Handle execution exceptions
                e.printStackTrace();
            }
        }

        // Initiates an orderly shutdown of the executor service
        executorService.shutdown();

        // Print customer seat assignments for each theater
        reservationSite.printCustomerSeatAssignments();

        // Verify that all customers booked between 1 and 3 seats
        boolean allBookingsValid = true;
        for (int seatsBooked : reservationSite.getCustomerSeatCountMap().values()) {
            if (seatsBooked < 1 || seatsBooked > 3) {
                allBookingsValid = false;
                break;
            }
        }
        // Print the result of the verification
        if (allBookingsValid) {
            System.out.println("All customers booked between 1 and 3 seats.");
        } else {
            System.out.println("Some customers did not book between 1 and 3 seats.");
        }
    }
}
