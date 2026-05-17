import com.sgbd.service.DataPopulationService;
import com.sgbd.util.DatabaseConnectionPool;

public class RunDataPop {
    public static void main(String[] args) {
        DatabaseConnectionPool.initialize();
        DataPopulationService svc = new DataPopulationService();
        svc.populateAll(1, 10);
        DatabaseConnectionPool.shutdown();
    }
}
