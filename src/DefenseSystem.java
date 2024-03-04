import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Random;

class Professor implements Runnable {
    private final CyclicBarrier barrier;
    private final CountDownLatch latch;

    public Professor(CyclicBarrier barrier, CountDownLatch latch) {
        this.barrier = barrier;
        this.latch = latch;
    }

    @Override
    public void run() {
        try {
            while (!Thread.interrupted() && latch.getCount() > 0) {
                barrier.await(); // cekaju se 2 studenta
            }
        } catch (InterruptedException | BrokenBarrierException e) {
            Thread.currentThread().interrupt();
        }
    }
}

class Assistant implements Runnable {
    private final Semaphore semaphore;
    private final CountDownLatch latch;

    public Assistant(Semaphore semaphore, CountDownLatch latch) {
        this.semaphore = semaphore;
        this.latch = latch;
    }

    @Override
    public void run() {
        try {
            while (!Thread.interrupted() && latch.getCount() > 0) {
                semaphore.acquire(); // ceka se student
                semaphore.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

class Student implements Runnable {
    private static final Random random = new Random();
    private static final AtomicInteger totalScore = new AtomicInteger(0);
    private static final AtomicInteger studentsCompleted = new AtomicInteger(0);
    private static final AtomicInteger studentsNotDefended = new AtomicInteger(0);

    private final String name;
    private final CyclicBarrier professorBarrier;
    private final Semaphore assistantSemaphore;
    private final CountDownLatch latch;
    private final double arrivalTime;
    private final double defenseTime;
    private final boolean defendWithProfessor;
    private final long startTime;

    public Student(String name, CyclicBarrier professorBarrier, Semaphore assistantSemaphore, CountDownLatch latch, long startTime) {
        this.name = name;
        this.professorBarrier = professorBarrier;
        this.assistantSemaphore = assistantSemaphore;
        this.latch = latch;
        this.arrivalTime = random.nextDouble();
        this.defenseTime = 0.5 + random.nextDouble() * 0.5;
        this.defendWithProfessor = random.nextBoolean();
        this.startTime = startTime;
    }

    @Override
    public void run() {
        try {
            Thread.sleep((long) (arrivalTime * 1000));
            long currentTime = System.currentTimeMillis();
            if (currentTime - startTime >= 5000) {
                System.out.printf("Thread: %s Arrival: %.2f Prof: - TTC: -:%.2f Score: - (Not defended)\n", name, arrivalTime, arrivalTime);
                studentsNotDefended.incrementAndGet();
                return;
            }
            String defenseType = defendWithProfessor ? "Professor" : "Assistant";
            int score = 5 + random.nextInt(6);
            if (defendWithProfessor) {
                professorBarrier.await();
//                System.out.printf("Thread: %s Arrival: %.2f Prof: Professor TTC: %.2f:%.2f Score: %d\n", name, arrivalTime, defenseTime, arrivalTime, score);
            } else {
                assistantSemaphore.acquire();
//                System.out.printf("Thread: %s Arrival: %.2f Prof: Assistant TTC: %.2f:%.2f Score: %d\n", name, arrivalTime, defenseTime, arrivalTime, score);
            }
            Thread.sleep((long) (defenseTime * 1000));
            currentTime = System.currentTimeMillis();
            if (currentTime - startTime < 5000) {
                System.out.printf("Thread: %s Arrival: %.2f Prof: %s TTC: %.2f:%.2f Score: %d\n", name, arrivalTime, defenseType, defenseTime, arrivalTime, score);
                totalScore.addAndGet(score);
                studentsCompleted.incrementAndGet();
            } else {
                System.out.printf("Thread: Odbrana studenta %s je prekinuta.\n", name);
                studentsNotDefended.incrementAndGet();
            }
        } catch (InterruptedException | BrokenBarrierException e) {
            Thread.currentThread().interrupt();
        } finally {
            latch.countDown();
            if (!defendWithProfessor) {
                assistantSemaphore.release();
            }
        }
    }

    public static double getAverageScore() {
        return studentsCompleted.get() == 0 ? 0 : (double) totalScore.get() / studentsCompleted.get();
    }

    public static int getStudentsNotDefended() {
        return studentsNotDefended.get();
    }
}

public class DefenseSystem {
    public static void main(String[] args) throws InterruptedException {
        int n = 100;
        CyclicBarrier professorBarrier = new CyclicBarrier(2);
        Semaphore assistantSemaphore = new Semaphore(1);
        CountDownLatch latch = new CountDownLatch(n);
        ExecutorService executor = Executors.newCachedThreadPool();
        long startTime = System.currentTimeMillis();

        Professor professor = new Professor(professorBarrier, latch);
        executor.execute(professor);
        executor.execute(professor);

        executor.execute(new Assistant(assistantSemaphore, latch));

        for (int i = 0; i < n; i++) {
            executor.execute(new Student("Student " + (i + 1), professorBarrier, assistantSemaphore, latch, startTime));
        }

        latch.await();
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.schedule(() -> {
            executor.shutdownNow();
        }, 5, TimeUnit.SECONDS);

        executor.awaitTermination(6, TimeUnit.SECONDS);
        executor.shutdownNow();
        System.out.printf("Prosecna ocena: %.2f\n", Student.getAverageScore());
        System.out.printf("Broj studenata koji nisu stigli da odbrane: %d\n", Student.getStudentsNotDefended());

        scheduler.shutdown();
    }
}