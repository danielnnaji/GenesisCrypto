package app.db;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

public abstract class DaoImpl<x extends ResultBean> {

    private Connection connection = null;
    private Statement statement = null;
    private List<x> data = new ArrayList<>();

    public DaoImpl(){}

    public abstract x getResultBean(ResultSet rs) throws SQLException;

    protected abstract String getSQL() throws ParseException;

    protected void executeQuery(Connection connection) throws Exception {

        this.connection = connection;
        this.statement = this.connection.createStatement();
        data = new ArrayList<>();

        mapResults(queryResults());
    }

    protected ResultSet queryResults() throws Exception{
        String sql = getSQL();
        return statement.executeQuery(sql);
    }

    public void mapResults(ResultSet rs) throws SQLException{
        while(rs.next()) {
            data.add(getResultBean(rs));
        }
    }

    public List<x> getData() { return this.data; }

}