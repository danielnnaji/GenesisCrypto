package app.db;

import com.binance.api.client.domain.account.NewOrderResponse;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class DataSourcer extends DaoImpl {
//    Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
    String connectionUrl = "jdbc:sqlserver://localhost:1433;database=TestDB;user=sa;password=Dan443aji10";

    ResultSet resultSet = null;
    @Override
    public ResultBean getResultBean(ResultSet rs){
        return null;
    }

    @Override
    public String getSQL(){
        return "";
    }

    public ConcurrentHashMap<Long, NewOrderResponse>  sourceData() throws ClassNotFoundException {
        return sourceWorkingOrders();

    }

    private ConcurrentHashMap<Long, NewOrderResponse>  sourceWorkingOrders() throws ClassNotFoundException {
        Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        ConcurrentHashMap<Long, NewOrderResponse> orders = new ConcurrentHashMap<>();
        try {

            Connection connection = DriverManager.getConnection(connectionUrl);
            Statement statement = connection.createStatement();

            // Create and execute a SELECT SQL statement.
            String selectSql = "SELECT * FROM dbo.WorkingOrder";

            resultSet = statement.executeQuery(selectSql);
//            connection.close();

            // Print results from select statement
            while (resultSet.next()) {
                NewOrderResponse workingOrder = new NewOrderResponse();
                workingOrder.hydrateBean(resultSet);
                orders.put(workingOrder.getOrderId(), workingOrder);
            }

        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return orders;
    }

    public void updateWorkingOrdersTable(ConcurrentHashMap<Long, NewOrderResponse>  workingOrders) throws ClassNotFoundException {
        Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        try {

            Connection connection = DriverManager.getConnection(connectionUrl);
            Statement statement = connection.createStatement();
            connection.setAutoCommit(false);

            StringBuilder stringBuilder = new StringBuilder();

            String updateSql = "TRUNCATE TABLE WorkingOrder \n";
            stringBuilder.append(updateSql);

            workingOrders.values().forEach(wo -> stringBuilder.append(wo.getInsertStatement()));

            updateSql = stringBuilder.toString();
            statement.executeUpdate(updateSql);
            connection.commit();

        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}

