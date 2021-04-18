import app.db.DataSourcer;
import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.NewOrderResponse;
import com.binance.api.client.domain.account.NewOrderResponseType;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.request.OrderStatusRequest;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.domain.market.TickerPrice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.binance.api.client.domain.account.NewOrder.*;

public class TestMain {

    private final static Logger LOG = Logger.getLogger(TestMain.class.getName());
    private final static long POLLING_TIME = 3600000L; // 1hr //30000L; // 30 sec in Milliseconds
    private static long lastOpenTime = 0L;
    private static boolean updatedOrders = false;

    public static void main(String[] args) throws Exception{
        Properties properties = parseProgramArgs(args);
        String APIKEY = "vFP09RtBgJ74kXrZ6h66LqtQTJTj3QwMhYEW5zgboC5I33vkRwpGAPZG3WAoH3Hf";//properties.getProperty("APIKEY");
        String SEC_KEY = "pG15M927ya0ashxAwEvl2pIhBFAVyY9VDkE9v3kYwykLUAxd0Cp2cw4Md3cZ0NQ3";//properties.getProperty("SEC_KEY");
        BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance(APIKEY, SEC_KEY);
        BinanceApiRestClient client = factory.newRestClient();
        ConcurrentHashMap<Long, NewOrderResponse> workingOrdersMap = new ConcurrentHashMap<>();



//        workingOrdersMap = getWorkingOrdersInDB(workingOrdersMap);
//
//        sell(client, workingOrdersMap);
//        updateWorkingOrders(client, workingOrdersMap);
//
//        //buy(client, workingOrdersMap);
//        //updateWorkingOrders(client, workingOrdersMap);
//
//        updateWorkingOrdersInDB(workingOrdersMap);

        divest(client);
        scheduler(client, workingOrdersMap);

        LOG.info("\n" +
              "************************************\n" +
                " GENESIS CRYPTO TRADER IS ONLINE...\n" +
                "************************************\n"
        );

    }

    private static Properties parseProgramArgs(String[]args){
        Properties properties = new Properties();
        for(String arg : args){
            String key = arg.split("=")[0];
            String value = arg.split("=")[1];
            properties.setProperty(key, value);
        }
        return properties;
    }
    private static ConcurrentHashMap<Long, NewOrderResponse> getWorkingOrdersInDB(ConcurrentHashMap<Long, NewOrderResponse> workingOrdersMap) throws Exception {
        DataSourcer dataSourcer = new DataSourcer();
        workingOrdersMap = dataSourcer.sourceData();
        LOG.info("Got " + workingOrdersMap.size() + " orders from DB\n");
        LOG.info(workingOrdersMap.values().toString());
        return workingOrdersMap;
    }

    private static void updateWorkingOrdersInDB(ConcurrentHashMap<Long, NewOrderResponse> workingOrdersMap) throws Exception {
        if(updatedOrders) {
            DataSourcer dataSourcer = new DataSourcer();
            dataSourcer.updateWorkingOrdersTable(workingOrdersMap);
            LOG.info("Updated orders in DB...\n");
            updatedOrders = false;
        }
    }

    private static void scheduler(BinanceApiRestClient client, ConcurrentHashMap<Long, NewOrderResponse> workingOrdersMap) {
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                //tryBuy(client, workingOrdersMap);
                //tryUpdate(client, workingOrdersMap);
//                trySell(client, workingOrdersMap);
//                tryUpdate(client, workingOrdersMap);
//                tryUpdateWorkingOrdersInDB(workingOrdersMap);
                divest(client);

            }
        }, POLLING_TIME, POLLING_TIME);
    }


    private static void tryBuy(BinanceApiRestClient client, ConcurrentHashMap<Long, NewOrderResponse> workingOrdersMap){
        try {
            buy(client, workingOrdersMap);
        } catch (Exception e) {
            LOG.warning("Unable to BUY\n" + e);
        }
    }

    private static void tryUpdate(BinanceApiRestClient client, ConcurrentHashMap<Long, NewOrderResponse> workingOrdersMap){
        updateWorkingOrders(client, workingOrdersMap);
//        try {
//            updateWorkingOrders(client, workingOrdersMap);
//        } catch (Exception e) {
//            LOG.warning("Unable to Update Working Order\n" + e);
//        }
    }

    private static void trySell(BinanceApiRestClient client, ConcurrentHashMap<Long, NewOrderResponse> workingOrdersMap){
        try {
            sell(client, workingOrdersMap);
        } catch (Exception e) {
            LOG.warning("Unable to SELL\n" + e);
        }
    }

    private static void tryUpdateWorkingOrdersInDB(ConcurrentHashMap<Long, NewOrderResponse> workingOrdersMap){
        try{
            updateWorkingOrdersInDB(workingOrdersMap);
        }catch (Exception e){
            LOG.warning("UNABLE TO UPDATE DB\n" + e);
        }
    }
    public static Double getAvgPx(BinanceApiRestClient client) {
        TickerPrice ap = client.getAvgPrice("BTCBUSD");
        return Double.parseDouble(ap.getPrice());
    }

    public static Double getCurrPx(BinanceApiRestClient client, String symbol) {
        TickerPrice cp = client.getPrice(symbol);
        return Double.parseDouble(cp.getPrice());
    }

    public static Candlestick getCandleStick(BinanceApiRestClient client){
        int limit = 1;
        return client.getCandlestickBars("BTCBUSD", CandlestickInterval.HOURLY, limit).get(0);
    }

    private static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = new BigDecimal(Double.toString(value));
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    private static void buy(BinanceApiRestClient client, ConcurrentHashMap<Long, NewOrderResponse> workingOrdersMap) {
        List<NewOrderResponse> workingBuyOrders = workingOrdersMap.values().stream().filter(x -> x.getSide() == OrderSide.BUY).collect(Collectors.toList());
//        List<NewOrderResponse> workingBuyOrders = workingOrdersMap.values().stream().filter(x -> ((x.getSide() == OrderSide.BUY) && (x.getStatus() == OrderStatus.NEW))).collect(Collectors.toList());


        double avgPx = 0;
        int threshold = 13;
        for(NewOrderResponse workingBuyOrder : workingBuyOrders){
            avgPx += Double.parseDouble(workingBuyOrder.getPrice());
        }
        avgPx = avgPx / workingBuyOrders.size();
        avgPx = avgPx - (avgPx * 0.015);

        Candlestick candlestick = getCandleStick(client);
        //Cache Stuff
        long openTime = candlestick.getOpenTime();
//        double open = Double.parseDouble(candlestick.getOpen());
//        double high = Double.parseDouble(candlestick.getHigh());
        double low = Double.parseDouble(candlestick.getLow());

//        if(lastOpenTime != openTime)


        ///

//        double ap = getAvgPx(client);
        double cp = getCurrPx(client, "BTCBUSD");

        if(((lastOpenTime != openTime) && (workingBuyOrders.size() < threshold)) || (cp < avgPx)) {

            if(low < cp){
                cp = low;
            }
//            if (cp > ap) {
            double qty = (5000 / 450.0);
            qty = qty / cp;
            qty = round(qty, 6);
            NewOrderResponse newOrderResponse = client.newOrder(limitBuy("BTCBUSD", TimeInForce.GTC, String.valueOf(qty), String.valueOf(cp)).newOrderRespType(NewOrderResponseType.FULL));
            OrderStatus orderStatus = newOrderResponse.getStatus();
            if (orderStatus != OrderStatus.REJECTED
                    && orderStatus != OrderStatus.CANCELED
                    && orderStatus != OrderStatus.EXPIRED
                    && orderStatus != OrderStatus.PENDING_CANCEL) {
                long orderId = newOrderResponse.getOrderId();
                workingOrdersMap.put(orderId, newOrderResponse); // Add new order to working orders map
                LOG.info("BOUGHT!!!\n" + newOrderResponse.toString() + "\n");
                updatedOrders = true;
            }

//            }
        }
        lastOpenTime = openTime;
    }


    private static void updateWorkingOrders(BinanceApiRestClient client, ConcurrentHashMap<Long, NewOrderResponse> workingOrdersMap) {
        for (NewOrderResponse workingOrder : workingOrdersMap.values()) {

            long orderId = workingOrder.getOrderId();
            String symbol = workingOrder.getSymbol();
            Order order = null;
            try {
                order = client.getOrderStatus(new OrderStatusRequest(symbol, orderId));
            }
            catch (Exception e) {
                LOG.warning("Unable to Update Working Order: \n" + workingOrder.toString() + "\n" + e);
            }
            if(order != null && workingOrder.getStatus() != order.getStatus()){
                LOG.info("Updating working order: " + workingOrder.toString() + "\n" + "To: " + order.toString());
                workingOrder.setStatus(order.getStatus());
                workingOrder.setExecutedQty(order.getExecutedQty());
                updatedOrders = true;
            }
        }
    }

    private static void sell(BinanceApiRestClient client, ConcurrentHashMap<Long, NewOrderResponse> workingOrdersMap) {
        for (NewOrderResponse workingOrder : workingOrdersMap.values()) {
            if (workingOrder.getSide() == OrderSide.BUY && (workingOrder.getStatus() == OrderStatus.FILLED || workingOrder.getStatus() == OrderStatus.PARTIALLY_FILLED)) {
                double cp = getCurrPx(client, "BTCBUSD");
                double workingOrderCurrPx = Double.parseDouble(workingOrder.getPrice());
                double buffer = 0.02;

                if (cp > (workingOrderCurrPx + (workingOrderCurrPx * buffer))) {
                    String qty = workingOrder.getExecutedQty();
                    String symbol = workingOrder.getSymbol();

                    LOG.info("Attempting to Sell: " + workingOrder);
                    NewOrderResponse newOrderResponse = client.newOrder(limitSell(symbol, TimeInForce.GTC, qty, String.valueOf(cp)).newOrderRespType(NewOrderResponseType.FULL));
                    OrderStatus orderStatus = newOrderResponse.getStatus();
                    if (orderStatus != OrderStatus.REJECTED
                            && orderStatus != OrderStatus.CANCELED
                            && orderStatus != OrderStatus.EXPIRED
                            && orderStatus != OrderStatus.PENDING_CANCEL) {

                        long orderId = newOrderResponse.getOrderId();
                        workingOrdersMap.put(orderId, newOrderResponse); // Add new order to working orders map
                        workingOrdersMap.remove(workingOrder.getOrderId()); // Remove
                        LOG.info("SOLD!!!\n" + newOrderResponse.toString() + "\n");
                        updatedOrders = true;
                    }

                }
            }
        }
    }

    private static void divest(BinanceApiRestClient client) {
        try {

        double ltcCurrPx = getCurrPx(client, "LTCBUSD"), ltcThreshold = 300, ltcTarget = 10.2, qty = 0,
//               ethCurrPx = getCurrPx(client, "ETHBUSD"), ethThreshold = 2000, ethTarget = 10.5,
               bnbCurrPx = getCurrPx(client, "BNBBUSD"), bnbThreshold = 550, bnbTarget = 10.2;


            //LTC
            if (ltcCurrPx >= ltcThreshold) {
                qty = round((ltcTarget / ltcCurrPx), 5);
                NewOrderResponse newOrderResponse = client.newOrder(
                        limitSell("LTCBUSD", TimeInForce.GTC, String.valueOf(qty), String.valueOf(ltcCurrPx))
                                .newOrderRespType(NewOrderResponseType.FULL));
                LOG.info("** SELL **\n" + newOrderResponse.toString() + "\n");
            }

            //ETH
//            if (ethCurrPx >= ethThreshold) {
//                qty = round((ethTarget / ethCurrPx), 5);
//                NewOrderResponse newOrderResponse = client.newOrder(
//                        limitSell("ETHBUSD", TimeInForce.GTC, String.valueOf(qty), String.valueOf(ethCurrPx))
//                                .newOrderRespType(NewOrderResponseType.FULL));
//                LOG.info("** SELL **\n" + newOrderResponse.toString() + "\n");
//            }

            //BNB
            if (bnbCurrPx >= bnbThreshold) {
                qty = round((bnbTarget / bnbCurrPx), 3);
                NewOrderResponse newOrderResponse = client.newOrder(
                        limitSell("BNBBUSD", TimeInForce.GTC, String.valueOf(qty), String.valueOf(bnbCurrPx))
                                .newOrderRespType(NewOrderResponseType.FULL));
                LOG.info("** SELL **\n" + newOrderResponse.toString() + "\n");
            }
        }

        catch (Exception e){
            LOG.warning(e.getMessage());
        }
    }
}
