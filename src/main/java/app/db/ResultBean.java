package app.db;

import java.sql.ResultSet;
import java.sql.SQLException;

public abstract class ResultBean {

    public abstract void hydrateBean(ResultSet resultSet) throws SQLException;
}