package shop;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShopServer {
    record Product(String id, String name, String category, String description, int price, int oldPrice, String image, String badge, int stock) {}
    private static final Map<String, Product> PRODUCTS = new LinkedHashMap<>();
    private static final List<Map<String, Object>> ORDERS = new ArrayList<>();
    private static final Object LOCK = new Object();
    private static final String WEB = "/web/";

    static {
        add("trail-pack", "The Trail Pack", "Bags", "A hard-wearing, weather-ready pack for all-day wandering.", 129, 159, "photo-1553062407-98eeb64c6a62", "BESTSELLER", 18);
        add("camp-mug", "Enamel Camp Mug", "Camp", "A dependable little mug for slow mornings outside.", 28, 0, "photo-1514228742587-6b1558fcca3d", "NEW", 32);
        add("field-jacket", "Field Jacket", "Apparel", "Light layers and plenty of pockets. Made for the in-between.", 184, 220, "photo-1591047139829-d91aecb6caea", "−16%", 11);
        add("weekender", "Canvas Weekender", "Bags", "Room for one more night. Built from sturdy recycled canvas.", 148, 0, "photo-1553062407-98eeb64c6a62", "", 14);
        add("trail-bottle", "Insulated Bottle", "Camp", "Keeps the cold cold and the hot hot. Leakproof, always.", 42, 52, "photo-1602143407151-7111542de6e8", "−19%", 24);
        add("wool-cap", "Everyday Wool Cap", "Apparel", "A soft, breathable layer for early starts and late finishes.", 38, 0, "photo-1588850561407-ed78c282e89b", "", 20);
        add("camp-light", "Pocket Lantern", "Camp", "Warm, dimmable light in a pocket-sized aluminum body.", 64, 0, "photo-1507473885765-e6ed057f782c", "STAFF PICK", 9);
        add("sling", "Daytrip Sling", "Bags", "The just-right carry for a camera, a snack, and the long way home.", 76, 92, "photo-1548036328-c9fa89d128fa", "−17%", 17);
    }

    private static void add(String id, String name, String category, String description, int price, int old, String photo, String badge, int stock) {
        PRODUCTS.put(id, new Product(id, name, category, description, price, old, "https://images.unsplash.com/" + photo + "?auto=format&fit=crop&w=900&q=85", badge, stock));
    }

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/products", ShopServer::products);
        server.createContext("/api/orders", ShopServer::orders);
        server.createContext("/api/", ShopServer::notFound);
        server.createContext("/", ShopServer::staticFile);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("Fieldwork is open at http://localhost:" + port);
    }

    private static void products(HttpExchange x) throws IOException {
        if (!x.getRequestMethod().equals("GET")) { send(x, 405, "{\"error\":\"Method not allowed\"}", "application/json"); return; }
        StringBuilder out = new StringBuilder("[" );
        for (Product p : PRODUCTS.values()) { if (out.length() > 1) out.append(','); out.append(productJson(p)); }
        out.append(']'); send(x, 200, out.toString(), "application/json");
    }

    private static String productJson(Product p) {
        return "{\"id\":"+q(p.id)+",\"name\":"+q(p.name)+",\"category\":"+q(p.category)+",\"description\":"+q(p.description)+",\"price\":"+p.price+",\"oldPrice\":"+p.oldPrice+",\"image\":"+q(p.image)+",\"badge\":"+q(p.badge)+",\"stock\":"+p.stock+"}";
    }

    private static void orders(HttpExchange x) throws IOException {
        if (x.getRequestMethod().equals("GET")) {
            synchronized (LOCK) { send(x, 200, json(ORDERS), "application/json"); }
            return;
        }
        if (!x.getRequestMethod().equals("POST")) { send(x, 405, "{\"error\":\"Method not allowed\"}", "application/json"); return; }
        try {
            String body = new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String name = field(body, "name"), email = field(body, "email"), address = field(body, "address");
            if (name.isBlank() || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || address.isBlank()) { send(x, 400, "{\"error\":\"Add a name, valid email, and delivery address.\"}", "application/json"); return; }
            Matcher rows = Pattern.compile("\\{[^{}]*\\}").matcher(body.substring(Math.min(body.indexOf("\"items\""), body.length())));
            List<Map<String,Object>> lines = new ArrayList<>(); int total = 0;
            synchronized (LOCK) {
                while (rows.find()) {
                    String row = rows.group(); String id = field(row, "id"); int qty = integer(row, "quantity"); Product p = PRODUCTS.get(id);
                    if (p == null || qty < 1 || qty > p.stock) { send(x, 400, "{\"error\":\"An item is unavailable in that quantity. Please refresh your cart.\"}", "application/json"); return; }
                    total += p.price * qty;
                    lines.add(Map.of("id", p.id, "name", p.name, "price", p.price, "quantity", qty));
                }
                if (lines.isEmpty()) { send(x, 400, "{\"error\":\"Your cart is empty.\"}", "application/json"); return; }
                String id = "FW-" + String.format("%05d", ORDERS.size() + 1);
                Map<String,Object> order = new LinkedHashMap<>(); order.put("id", id); order.put("name", name); order.put("email", email); order.put("address", address); order.put("items", lines); order.put("total", total); order.put("status", "Confirmed"); order.put("createdAt", java.time.Instant.now().toString());
                ORDERS.add(order); send(x, 201, json(order), "application/json");
            }
        } catch (Exception e) { send(x, 400, "{\"error\":\"We couldn't read that order. Please try again.\"}", "application/json"); }
    }

    private static int integer(String s, String key) { Matcher m=Pattern.compile("\\\""+key+"\\\"\\s*:\\s*(\\d+)").matcher(s); return m.find()?Integer.parseInt(m.group(1)):0; }
    private static String field(String s, String key) { Matcher m=Pattern.compile("\\\""+key+"\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"").matcher(s); if(!m.find())return ""; return m.group(1).replace("\\\"","\"").replace("\\n"," ").replace("\\\\","\\"); }
    private static String q(String s) { return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n"," ")+"\""; }
    private static String json(Object v) {
        if (v instanceof String s) return q(s);
        if (v instanceof Number || v instanceof Boolean) return v.toString();
        if (v instanceof Map<?,?> m) { List<String> a=new ArrayList<>(); m.forEach((k,val)->a.add(q(k.toString())+":"+json(val))); return "{"+String.join(",",a)+"}"; }
        if (v instanceof Collection<?> c) { List<String> a=new ArrayList<>(); c.forEach(i->a.add(json(i))); return "["+String.join(",",a)+"]"; }
        return "null";
    }
    private static void notFound(HttpExchange x) throws IOException { send(x,404,"{\"error\":\"Not found\"}","application/json"); }
    private static void staticFile(HttpExchange x) throws IOException {
        if (!x.getRequestMethod().equals("GET")) { send(x,405,"Method not allowed","text/plain"); return; }
        String path=x.getRequestURI().getPath(); if(path.equals("/")) path="/index.html";
        if(path.contains("..") || path.contains("\\")) { send(x,400,"Bad path","text/plain"); return; }
        try (var in=ShopServer.class.getResourceAsStream(WEB+path.substring(1))) {
            if(in==null) { send(x,404,"Not found","text/plain"); return; }
            String type=path.endsWith(".css")?"text/css; charset=utf-8":path.endsWith(".js")?"text/javascript; charset=utf-8":"text/html; charset=utf-8";
            send(x,200,new String(in.readAllBytes(),StandardCharsets.UTF_8),type);
        }
    }
    private static void send(HttpExchange x,int code,String body,String type) throws IOException { byte[] bytes=body.getBytes(StandardCharsets.UTF_8); x.getResponseHeaders().set("Content-Type",type); x.getResponseHeaders().set("Cache-Control","no-store"); x.sendResponseHeaders(code,bytes.length); x.getResponseBody().write(bytes); x.close(); }
}
