import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class PeriodTracker {
    private static final String FOLDER_NAME = "cycle_data";
    private static final String PREDICTION_FILE = "predictionData.txt";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Global variables
    private static int endedChoice;
    private static String periodEndDate; // Will be set when user enters end date
    private static LocalDate currentDate;
    private static LocalDate predictedStartDate;
    private static LocalDate predictedEndDate;
    private static LocalDate lastStartDate; // Actual start date of the current cycle
    private static int periodLength = 7; // Default period length
    private static int cycleGap = 28;    // Default cycle gap
    private static List<CycleEntry> cycleDatabase = new ArrayList<>();
    
    // Flag to indicate that a cycle end has been confirmed so no new cycle is prompted immediately.
    private static boolean cycleEndedConfirmed = false;
    
    public static void main(String[] args) {
        createDataFolder();
        cycleDatabase = loadCycleData();
        loadPredictionData();
        currentDate = LocalDate.now();
    
        try (Scanner scanner = new Scanner(System.in)) {
            checkForUpcomingPeriod(scanner);
            // Only prompt for new cycle data if a cycle end was NOT just confirmed.
            if (!cycleEndedConfirmed) {
                enterCycleData(scanner);
            }
        }
    }
    
    private static void createDataFolder() {
        File folder = new File(FOLDER_NAME);
        if (!folder.exists()) {
            folder.mkdir();
        }
    }
    
    // Loads cycle data from files in the folder.
    private static List<CycleEntry> loadCycleData() {
        List<CycleEntry> data = new ArrayList<>();
        File folder = new File(FOLDER_NAME);
        File[] files = folder.listFiles();
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (File file : files) {
                try {
                    List<String> lines = Files.readAllLines(file.toPath());
                    if (lines.size() == 2) {
                        data.add(new CycleEntry(lines.get(0), lines.get(1)));
                    }
                } catch (IOException e) {
                    System.out.println("Error reading file: " + file.getName());
                }
            }
        }
        // If data exists, update lastStartDate and set default predictions.
        if (!data.isEmpty()) {
            CycleEntry lastCycle = data.get(data.size() - 1);
            lastStartDate = LocalDate.parse(lastCycle.startDate, formatter);
            // For initial predictions, we use default gap and period length.
            predictedStartDate = lastStartDate.plusDays(cycleGap);
            predictedEndDate = predictedStartDate.plusDays(periodLength);
        } else {
            lastStartDate = null;
        }
        return data;
    }
    
    // Prompts the user to enter new cycle data (start and end dates).
    private static void enterCycleData(Scanner scanner) {
        System.out.println("Enter cycle START date (YYYY-MM-DD):");
        String periodStartDate = scanner.nextLine();
        lastStartDate = LocalDate.parse(periodStartDate, formatter);
    
        if (predictedStartDate == null) {
            System.out.println("✅ No previous predictions available. Starting fresh with this cycle.");
            predictedStartDate = lastStartDate;
            predictedEndDate = predictedStartDate.plusDays(periodLength);
        } else if (lastStartDate.equals(predictedStartDate)) {
            System.out.println("\uD83C\uDF89 Congratulations! Your cycle started on the predicted date.");
            deleteOldCycleData(predictedStartDate.format(formatter));
        } else {
            System.out.println("⚠️ Your cycle did not start on the predicted date. Recording actual start date.");
            deleteOldCycleData(lastStartDate.format(formatter));
            // Overwrite prediction with actual start date.
            predictedStartDate = lastStartDate;
            predictedEndDate = predictedStartDate.plusDays(periodLength);
        }
    
        // Ask if the period has ended.
        System.out.println("Has your period ended? (1) Yes (2) No");
        endedChoice = scanner.nextInt();
        scanner.nextLine(); // consume newline
    
        if (endedChoice == 1) {
            System.out.println("Enter cycle END date (YYYY-MM-DD):");
            periodEndDate = scanner.nextLine();
            periodLength = calculateDays(periodStartDate, periodEndDate);
            cycleGap = 28; // default gap
            saveCycleData(periodStartDate, periodEndDate);
            predictNextCycle(periodEndDate, periodLength, cycleGap);
            cycleEndedConfirmed = true;
        }  else {
            System.out.println("Enter end date when available. Here are tips for menstrual discomfort:");
            System.out.println("- Apply heat to relax muscles\n- Exercise to release pain-blocking endorphins");
            System.out.println("- Reduce stress to ease pain perception\n- Get vitamins and minerals through a healthy diet");
            System.out.println("\uD83D\uDD14 Your period is expected to end on: " + predictedEndDate);
            System.out.println("✅ I will remind you to confirm when it ends.");
            
            // Overwrite the previous cycle data's start date with the new cycle start date.
            deleteOldCycleData(periodStartDate);
            // Save the new start date with a placeholder for end date ("pending").
            saveCycleData(periodStartDate, "pending");
        }
    }
    
    // When today is the predicted end date, ask the user to confirm the actual end date.
    private static void confirmCycleEnd(Scanner scanner) {
        if (lastStartDate == null) {
            System.out.println("⚠️ Error: No recorded start date. Please enter the start date again.");
            enterCycleData(scanner);
            return;
        }
    
        System.out.println("Enter cycle END date (YYYY-MM-DD):");
        String inputEndDate = scanner.nextLine();
        LocalDate confirmedEnd = LocalDate.parse(inputEndDate, formatter);
    
        if (predictedEndDate != null && confirmedEnd.equals(predictedEndDate)) {
            System.out.println("🎉 Congratulations! Your cycle ended on the predicted date.");
        } else {
            System.out.println("⚠️ Your cycle is irregular. It did not end on the predicted date.");
        }
    
        // Overwrite old cycle data with the confirmed data
        saveCycleData(lastStartDate.format(formatter), inputEndDate);
    
        // Update period length based on actual data
        periodLength = calculateDays(lastStartDate.format(formatter), inputEndDate);
        cycleGap = 28; // using default gap
    
        // Compute new predictions based on confirmed end date
        LocalDate newPredictedStart = confirmedEnd.plusDays(cycleGap);
        predictedStartDate = newPredictedStart;
        predictedEndDate = predictedStartDate.plusDays(periodLength);
    
        // Delete the old prediction file if the start date has changed
        deleteOldCycleData(lastStartDate.format(formatter));
    
        savePredictionData(predictedStartDate, predictedEndDate);
        saveCycleData(predictedStartDate.format(formatter), predictedEndDate.format(formatter));
    
        System.out.println("✅ Predictions updated successfully. Next cycle saved!");
        cycleEndedConfirmed = true;
    }
    
    // Predicts the next cycle's start and end dates based on the given period end date.
    private static void predictNextCycle(String periodEndDate, int periodLength, int cycleGap) {
        LocalDate end = LocalDate.parse(periodEndDate, formatter);
        predictedStartDate = end.plusDays(cycleGap);
        predictedEndDate = predictedStartDate.plusDays(periodLength);
    
        System.out.println("📅 Your next period is expected to start on: " + predictedStartDate);
        System.out.println("📅 Your next period is expected to end on: " + predictedEndDate);
        notifyUser(predictedStartDate);
    
        // Save new predictions.
        savePredictionData(predictedStartDate, predictedEndDate);
    }
    
    // Saves cycle data (start and end dates) into a file named by the start date.
    private static void saveCycleData(String periodStartDate, String periodEndDate) {
        String fileName = FOLDER_NAME + "/" + periodStartDate + ".txt";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            writer.write(periodStartDate + "\n" + periodEndDate);
            System.out.println("✅ Cycle data saved for: " + periodStartDate + " - " + periodEndDate);
        } catch (IOException e) {
            System.out.println("❌ Error saving cycle data.");
        }
    }
    
    // Saves predicted cycle dates into a prediction file.
    private static void savePredictionData(LocalDate startDate, LocalDate endDate) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(PREDICTION_FILE))) {
            writer.write(startDate.format(formatter) + "\n" + endDate.format(formatter));
        } catch (IOException e) {
            System.out.println("Error saving prediction data.");
        }
    }
    
    // Loads predicted cycle dates from the prediction file.
    private static void loadPredictionData() {
        File file = new File(PREDICTION_FILE);
        if (file.exists()) {
            try {
                List<String> lines = Files.readAllLines(file.toPath());
                if (lines.size() == 2) {
                    predictedStartDate = LocalDate.parse(lines.get(0), formatter);
                    predictedEndDate = LocalDate.parse(lines.get(1), formatter);
                } else {
                    predictedStartDate = null;
                    predictedEndDate = null;
                }
            } catch (IOException e) {
                System.out.println("Error loading prediction data.");
                predictedStartDate = null;
                predictedEndDate = null;
            }
        } else {
            predictedStartDate = null;
            predictedEndDate = null;
        }
    }
    
    // Deletes the cycle data file with the given start date.
    private static void deleteOldCycleData(String startDate) {
        String fileName = FOLDER_NAME + "/" + startDate + ".txt";
        File file = new File(fileName);
        if (file.exists()) {
            if (file.delete()) {
                System.out.println("✅ Deleted old cycle data for start date: " + startDate);
            } else {
                System.out.println("⚠️ Failed to delete old cycle data for start date: " + startDate);
            }
        }
    }
    
    // Calculates the number of days between two dates.
    private static int calculateDays(String date1, String date2) {
        LocalDate d1 = LocalDate.parse(date1, formatter);
        LocalDate d2 = LocalDate.parse(date2, formatter);
        return (int) java.time.temporal.ChronoUnit.DAYS.between(d1, d2);
    }
    
    // Calculates the average period length from the cycle database.
    private static int calculateAveragePeriodLength(List<CycleEntry> cycleDatabase) {
        int totalLength = 0;
        for (CycleEntry entry : cycleDatabase) {
            totalLength += calculateDays(entry.startDate, entry.endDate);
        }
        return (cycleDatabase.size() > 0) ? totalLength / cycleDatabase.size() : periodLength;
    }
    
    // Checks if the current date matches any upcoming prediction conditions.
    private static void checkForUpcomingPeriod(Scanner scanner) {
        LocalDate now = LocalDate.now();
        if (predictedStartDate != null && predictedEndDate != null) {
            if (now.plusDays(3).equals(predictedStartDate)) {
                System.out.println("🔔 NOTIFICATION: Your period is approaching. Be prepared!");
            } else if (now.equals(predictedStartDate)) {
                System.out.println("🔔 NOTIFICATION: Your period is expected today. Please confirm.");
            } else if (now.equals(predictedEndDate)) {
                System.out.println("🔔 NOTIFICATION: Your period should end today. Please confirm.");
                confirmCycleEnd(scanner);
            }
        }
    }
    
    // Notifies the user of the next predicted start date.
    private static void notifyUser(LocalDate predictedStartDate) {
        System.out.println("🔔 NOTIFICATION: Your next period is expected to start on " + predictedStartDate);
    }
}

class CycleEntry {
    String startDate;
    String endDate;
    
    CycleEntry(String startDate, String endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }
}
