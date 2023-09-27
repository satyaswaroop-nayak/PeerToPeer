import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

public class test {

    private static final Object lock = new Object();
    static ReentrantLock tlock = new ReentrantLock(true);

    public static void main(String[] args) {
        // ExecutorService executorService = Executors.newFixedThreadPool(10);
        for (int i = 0; i < 10; i++) {
            // executorService.submit(new CThread());
            new Thread(new CThread()).start();
        }
    }

    public static void accessMethod() {
        tlock.lock();
        try {
            int i = 100000000;
            while (i-- > 0)
                ;
            System.out.println("Test");
        } finally {
            tlock.unlock();
        }
    }

    static public class CThread implements Runnable {

        @Override
        public void run() {
            // TODO Auto-generated method stub
            // throw new UnsupportedOperationException("Unimplemented method 'run'");
            accessMethod();
        }

    }
}