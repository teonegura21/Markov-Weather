import com.sgbd.util.DatabaseConnectionPool;
import com.sgbd.util.DatabaseInitializer;

public class RunDbInit {
    public static void main(String[] args) {
        DatabaseConnectionPool.initialize();
        boolean ok = DatabaseInitializer.initialize();
        System.out.println("Init result: " + ok);
        DatabaseConnectionPool.shutdown();
    }
}
