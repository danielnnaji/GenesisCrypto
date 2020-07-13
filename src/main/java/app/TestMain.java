package app;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.NewOrderResponse;
import com.binance.api.client.domain.account.NewOrderResponseType;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.request.OrderStatusRequest;
import com.binance.api.client.domain.market.TickerPrice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.binance.api.client.domain.account.NewOrder.*;

public class TestMain {

    private final static Logger LOG = Logger.getLogger(TestMain.class.getName());
    private final static long POLLING_TIME = 10000L; // 10 sec

    public static void main(String[] args) {
        String APIKEY = "dZ5leU4Xs3zXHeHM8RLD058rpV1wI4MiUgXJDgmb2no38LrReoikShE09qqEIhc8";
        String SEC_KEY = "PWtdC42BDq4su8yn0uApJLiiNaCv0uIcA2UPv0vh0VTbar3Hyy09DBsvfSjbhHcm";
        BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance(APIKEY, SEC_KEY);
        BinanceApiRestClient client = factory.newRestClient();
        ConcurrentHashMap<Long, NewOrderResponse> workingOrdersMap = new ConcurrentHashMap<>();



        // BUY
        buy(client, workingOrdersMap);

        sell(client, workingOrdersMap);

        scheduler(client, workingOrdersMap);


    }

    private static void scheduler(BinanceApiRestClient client, ConcurrentHashMap<Long, NewOrderResponse> workingOrdersMap) {
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                tryBuy(client, workingOrdersMap);
                tryUpdate(client, workingOrdersMap);
                trySell(client, workingOrdersMap);
                tryUpdate(client, workingOrdersMap);
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

    public static Double getAvgPx(BinanceApiRestClient client) {
        TickerPrice ap = client.getAvgPrice("BTCBUSD");
        return Double.valueOf(ap.getPrice());
    }

    public static Double getCurrPx(BinanceApiRestClient client) {
        TickerPrice cp = client.getPrice("BTCBUSD");
        return Double.valueOf(cp.getPrice());
    }

    private static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = new BigDecimal(Double.toString(value));
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    private static void buy(BinanceApiRestClient client, ConcurrentHashMap<Long, NewOrderResponse> workingOrdersMap) {
        List<NewOrderResponse> workingBuyOrders = workingOrdersMap.values().stream().filter(x -> x.getSide() == OrderSide.BUY).collect(Collectors.toList());

        double avgPx = 0;
        int threshold = 5;
        for(NewOrderResponse workingBuyOrder : workingBuyOrders){
            avgPx += Double.valueOf(workingBuyOrder.getPrice());
        }
        avgPx = avgPx / workingBuyOrders.size();
        avgPx = avgPx - (avgPx * 0.03);

        double ap = getAvgPx(client);
        double cp = getCurrPx(client);

        if(workingBuyOrders.size() < threshold || cp < avgPx) {

            if (cp > ap) {
                double qty = (5000 / 450.0);
                qty = qty / cp;

                qty = round(qty, 6);

                NewOrderResponse newOrderResponse = client.newOrder(limitBuy("BTCBUSD", TimeInForce.FOK, String.valueOf(qty), String.valueOf(cp)).newOrderRespType(NewOrderResponseType.FULL));
                OrderStatus orderStatus = newOrderResponse.getStatus();
                if (orderStatus != OrderStatus.REJECTED
                        && orderStatus != OrderStatus.CANCELED
                        && orderStatus != OrderStatus.EXPIRED
                        && orderStatus != OrderStatus.PENDING_CANCEL) {

                    long orderId = newOrderResponse.getOrderId();
                    workingOrdersMap.put(orderId, newOrderResponse); // Add new order to working orders map
                    LOG.info("BOUGHT!!!\n" + newOrderResponse.toString() + "\n");
                }

            }
        }
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
            }
        }
    }

    private static void sell(BinanceApiRestClient client, ConcurrentHashMap<Long, NewOrderResponse> workingOrdersMap) {
        for (NewOrderResponse workingOrder : workingOrdersMap.values()) {
            if (workingOrder.getSide() == OrderSide.BUY && (workingOrder.getStatus() == OrderStatus.FILLED || workingOrder.getStatus() == OrderStatus.PARTIALLY_FILLED)) {
                double cp = getCurrPx(client);
                double workingOrderCurrPx = Double.valueOf(workingOrder.getPrice());
                double buffer = 0.015;

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
                    }

                }
            }
        }
    }
}
