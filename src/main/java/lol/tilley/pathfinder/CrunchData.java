package lol.tilley.pathfinder;

import com.google.gson.*;

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class CrunchData {

    public record RoadSegment(double p1X, double p1Z, double p2X, double p2Z) {}
    public record Road(String name, boolean paved, List<RoadSegment> segments) {}
    private static List<Road> cachedNetwork = null;
    private static List<Road> currentPath = new ArrayList<>();
    private static final List<double[]> currentSteps = new ArrayList<>();
    private static final List<String> currentDirections = new ArrayList<>();
    private static final List<String> currentRoadNames = new ArrayList<>();
    public static List<String> getRoadNames() { return currentRoadNames; }

    public static List<Road> fetchRoads(String jsonUrl) throws Exception {
        var conn = (HttpURLConnection) new URL(jsonUrl).openConnection();
        var sb = new StringBuilder();
        try (var r = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return parseRoads(JsonParser.parseString(sb.toString()).getAsJsonObject());
    }

    private static List<Road> parseRoads(JsonObject root) {
        var roads = new ArrayList<Road>();
        var list = root.getAsJsonObject("expressions").getAsJsonArray("list");
        var folders = new HashMap<String, String>();
        for (var el : list) {
            var item = el.getAsJsonObject();
            if (item.get("type").getAsString().equals("folder"))
                folders.put(item.get("id").getAsString(), item.get("title").getAsString());
        }
        String pending = null;
        for (var el : list) {
            var item = el.getAsJsonObject();
            var type = item.get("type").getAsString();
            if (type.equals("text") && item.has("folderId")) {
                pending = item.get("text").getAsString();
            } else if (type.equals("table")) {
                var name = pending != null ? pending : folders.getOrDefault(item.has("folderId") ? item.get("folderId").getAsString() : "", "Unknown");
                pending = null;
                var cols = item.getAsJsonArray("columns");
                JsonArray xVals = null, yVals = null;
                String color = null;
                for (var colEl : cols) {
                    var col = colEl.getAsJsonObject();
                    if (col.has("lines") && col.get("lines").getAsBoolean()) {
                        color = col.get("color").getAsString();
                        yVals = col.getAsJsonArray("values");
                    } else if (col.has("hidden") && col.get("hidden").getAsBoolean()) {
                        xVals = col.getAsJsonArray("values");
                    }
                }
                if (color == null || color.equals("#2d70b3") || xVals == null || yVals == null) continue;
                roads.add(new Road(name, color.equals("#000000"), parseSegments(xVals, yVals)));
            }
        }
        return roads;
    }

    public static List<Road> subdivideSegments(List<Road> roads) {
        var result = new ArrayList<Road>();
        for (int r = 0; r < roads.size(); r++) {
            var road = roads.get(r);
            var newSegs = new ArrayList<RoadSegment>();
            for (var seg : road.segments()) {
                var tValues = new TreeSet<Double>();
                tValues.add(0.0);
                tValues.add(1.0);
                for (int r2 = 0; r2 < roads.size(); r2++) {
                    if (r == r2) continue;
                    for (var other : roads.get(r2).segments())  {
                        Double t = intersectT(seg, other);
                        if (t != null) tValues.add(t);
                    }
                }
                var ts = new ArrayList<>(tValues);
                for (int i = 0; i < ts.size() - 1; i++) {
                    double t1 = ts.get(i), t2 = ts.get(i + 1);
                    newSegs.add(new RoadSegment(
                            lerp(seg.p1X(), seg.p2X(), t1), lerp(seg.p1Z(), seg.p2Z(), t1),
                            lerp(seg.p1X(), seg.p2X(), t2), lerp(seg.p1Z(), seg.p2Z(), t2)
                    ));
                }
            }
            result.add(new Road(road.name(), road.paved(), newSegs));
        }
        return result;
    }

    private static Double intersectT(RoadSegment a, RoadSegment b) {
        double dx1 = a.p2X() - a.p1X(), dy1 = a.p2Z() - a.p1Z();
        double dx2 = b.p2X() - b.p1X(), dy2 = b.p2Z() - b.p1Z();
        double denom = dx1 * dy2 - dy1 * dx2;
        if (Math.abs(denom) < 1e-10) return null;
        double t = ((b.p1X() - a.p1X()) * dy2 - (b.p1Z() - a.p1Z()) * dx2) / denom;
        double s = ((b.p1X() - a.p1X()) * dy1 - (b.p1Z() - a.p1Z()) * dx1) / denom;
        return (t >= 0 && t <= 1 && s >= 0 && s <= 1) ? t : null;
    }

    public static double lerp(double a, double b, double t) { return a + t * (b - a); }

    private static List<RoadSegment> parseSegments(JsonArray xVals, JsonArray yVals) {
        var segs = new ArrayList<RoadSegment>();
        int len = Math.min(xVals.size(), yVals.size());
        for (int i = 0; i < len - 1; i++) {
            var x1 = xVals.get(i).getAsString(); var y1 = yVals.get(i).getAsString();
            var x2 = xVals.get(i + 1).getAsString(); var y2 = yVals.get(i + 1).getAsString();
            if (x1.isEmpty() || y1.isEmpty() || x2.isEmpty() || y2.isEmpty()) continue;
            segs.add(new RoadSegment(Double.parseDouble(x1), -Double.parseDouble(y1), Double.parseDouble(x2), -Double.parseDouble(y2)));
        }
        return segs;
    }

    public static List<Road> includePosition(double customX, double customY, List<Road> highwayNetwork) {
        double bestDist = Double.MAX_VALUE;
        int bestRoad = -1, bestSeg = -1;
        double bestT = 0;
        for (int r = 0; r < highwayNetwork.size(); r++) {
            var segs = highwayNetwork.get(r).segments();
            for (int s = 0; s < segs.size(); s++) {
                double t = projectT(customX, customY, segs.get(s));
                double px = lerp(segs.get(s).p1X(), segs.get(s).p2X(), t);
                double py = lerp(segs.get(s).p1Z(), segs.get(s).p2Z(), t);
                double dist = Math.hypot(customX - px, customY - py);
                if (dist < bestDist) { bestDist = dist; bestRoad = r; bestSeg = s; bestT = t; }
            }
        }
        var target = highwayNetwork.get(bestRoad).segments().get(bestSeg);
        double snapX = lerp(target.p1X(), target.p2X(), bestT);
        double snapY = lerp(target.p1Z(), target.p2Z(), bestT);
        var result = new ArrayList<Road>();
        for (int r = 0; r < highwayNetwork.size(); r++) {
            var road = highwayNetwork.get(r);
            if (r == bestRoad && bestT > 1e-10 && bestT < 1 - 1e-10) {
                var newSegs = new ArrayList<>(road.segments());
                newSegs.set(bestSeg, new RoadSegment(target.p1X(), target.p1Z(), snapX, snapY));
                newSegs.add(bestSeg + 1, new RoadSegment(snapX, snapY, target.p2X(), target.p2Z()));
                result.add(new Road(road.name(), road.paved(), newSegs));
            } else {
                result.add(road);
            }
        }
        if (bestDist > 1e-6)
            result.add(new Road("Open Nether", false, List.of(new RoadSegment(customX, customY, snapX, snapY))));
        return result;
    }

    private static double projectT(double px, double py, RoadSegment seg) {
        double dx = seg.p2X() - seg.p1X(), dy = seg.p2Z() - seg.p1Z();
        double len2 = dx * dx + dy * dy;
        if (len2 < 1e-20) return 0;
        return Math.max(0, Math.min(1, ((px - seg.p1X()) * dx + (py - seg.p1Z()) * dy) / len2));
    }


    public static List<Road> snapToGrid(List<Road> network) {
        var result = new ArrayList<Road>();
        for (var road : network) {
            var segs = road.segments().stream()
                    .map(s -> new RoadSegment(Math.round(s.p1X()), Math.round(s.p1Z()), Math.round(s.p2X()), Math.round(s.p2Z())))
                    .filter(s -> s.p1X() != s.p2X() || s.p1Z() != s.p2Z())
                    .toList();
            if (!segs.isEmpty()) result.add(new Road(road.name(), road.paved(), segs));
        }
        return result;
    }

    public static List<Road> mergeCollinear(List<Road> network) {
        var result = new ArrayList<Road>();
        for (var road : network) {
            var segs = new ArrayList<RoadSegment>();
            for (var seg : road.segments()) {
                if (!segs.isEmpty()) {
                    var last = segs.get(segs.size() - 1);
                    if (Math.abs(last.p2X() - seg.p1X()) < 1e-6 && Math.abs(last.p2Z() - seg.p1Z()) < 1e-6) {
                        double dx1 = last.p2X() - last.p1X(), dy1 = last.p2Z() - last.p1Z();
                        double dx2 = seg.p2X() - seg.p1X(), dy2 = seg.p2Z() - seg.p1Z();
                        double cross = dx1 * dy2 - dy1 * dx2;
                        double mag = Math.sqrt((dx1 * dx1 + dy1 * dy1) * (dx2 * dx2 + dy2 * dy2));
                        if (mag < 1e-20 || Math.abs(cross / mag) < 1e-9) {
                            segs.set(segs.size() - 1, new RoadSegment(last.p1X(), last.p1Z(), seg.p2X(), seg.p2Z()));
                            continue;
                        }
                    }
                }
                segs.add(seg);
            }
            result.add(new Road(road.name(), road.paved(), segs));
        }
        return result;
    }

    public static List<Road> renameRoads(List<Road> network) {
        var result = new ArrayList<Road>();
        for (var road : network) {
            if (road.name().contains("Cardinal") || road.name().contains("Diagonal")) {
                var segs = new ArrayList<RoadSegment>();
                for (var seg : road.segments()) segs.addAll(splitAtOrigin(seg));
                var byDir = new LinkedHashMap<String, List<RoadSegment>>();
                for (var seg : segs)
                    byDir.computeIfAbsent(directionName(seg), k -> new ArrayList<>()).add(seg);
                for (var e : byDir.entrySet())
                    result.add(new Road(e.getKey(), road.paved(), e.getValue()));
            } else if (road.name().toLowerCase().contains("grid")) {
                var byLine = new LinkedHashMap<String, List<RoadSegment>>();
                for (var seg : road.segments()) {
                    boolean vertical = Math.abs(seg.p1X() - seg.p2X()) < 1e-6;
                    if (Math.abs(vertical ? seg.p1X() : seg.p1Z()) < 1e-6) continue;
                    byLine.computeIfAbsent(gridName(seg), k -> new ArrayList<>()).add(seg);
                }
                for (var e : byLine.entrySet())
                    result.add(new Road(e.getKey(), road.paved(), e.getValue()));
            } else if (road.name().toLowerCase().contains("ring") && road.segments().stream().allMatch(s -> Math.max(Math.abs(s.p1X()), Math.abs(s.p1Z())) < 50000 && Math.round(Math.max(Math.abs(s.p1X()), Math.abs(s.p1Z()))) % 5000 == 0)) {
            } else if (road.name().toLowerCase().contains("ring") || road.name().toLowerCase().contains("diamond")) {
                result.add(new Road(road.name().substring(0, road.name().lastIndexOf(' ')), road.paved(), road.segments()));
            } else {
                result.add(new Road(road.name().replace(" road", "").replace("road", ""), road.paved(), road.segments()));
            }
        }
        return result;
    }

    private static String directionName(RoadSegment seg) {
        double mx = (seg.p1X() + seg.p2X()) / 2, mz = (seg.p1Z() + seg.p2Z()) / 2;
        boolean n = mz < -1e-6, s = mz > 1e-6, e = mx > 1e-6, w = mx < -1e-6;
        String dir = n && e ? "northeast" : n && w ? "northwest" : s && e ? "southeast" : s && w ? "southwest"
                : n ? "northern" : s ? "southern" : e ? "eastern" : w ? "western" : "???";
        return dir + " superhighway";
    }

    private static List<RoadSegment> splitAtOrigin(RoadSegment seg) {
        double dx = seg.p2X() - seg.p1X(), dy = seg.p2Z() - seg.p1Z();
        double len2 = dx * dx + dy * dy;
        if (len2 < 1e-20) return List.of(seg);
        double t = -(seg.p1X() * dx + seg.p1Z() * dy) / len2;
        if (t <= 1e-10 || t >= 1 - 1e-10) return List.of(seg);
        double ox = seg.p1X() + t * dx, oy = seg.p1Z() + t * dy;
        if (Math.hypot(ox, oy) > 1e-6) return List.of(seg);
        return List.of(new RoadSegment(seg.p1X(), seg.p1Z(), 0, 0), new RoadSegment(0, 0, seg.p2X(), seg.p2Z()));
    }

    public static List<Road> findPath(double startX, double startY, double endX, double endY, List<Road> network) {
        double[] snapStart = nearestPoint(startX, startY, network);
        double[] snapEnd = nearestPoint(endX, endY, network);
        var split = new ArrayList<>(snapToGrid(includePosition(snapEnd[0], snapEnd[1], includePosition(snapStart[0], snapStart[1], network))));
        split.removeIf(r -> r.name().equals("Open Nether"));
        long sx = Math.round(snapStart[0]), sy = Math.round(snapStart[1]);
        long ex = Math.round(snapEnd[0]), ey = Math.round(snapEnd[1]);
        record Edge(String from, String to, double dist, int road, int seg) {}
        record Node(double f, double g, String key) {}
        var adj = new HashMap<String, List<Edge>>();
        var coords = new HashMap<String, double[]>();
        for (int r = 0; r < split.size(); r++)
            for (int s = 0; s < split.get(r).segments().size(); s++) {
                var seg = split.get(r).segments().get(s);
                String k1 = nodeKey(seg.p1X(), seg.p1Z()), k2 = nodeKey(seg.p2X(), seg.p2Z());
                double d = Math.hypot(seg.p2X() - seg.p1X(), seg.p2Z() - seg.p1Z());
                adj.computeIfAbsent(k1, k -> new ArrayList<>()).add(new Edge(k1, k2, d, r, s));
                adj.computeIfAbsent(k2, k -> new ArrayList<>()).add(new Edge(k2, k1, d, r, s));
                coords.putIfAbsent(k1, new double[]{seg.p1X(), seg.p1Z()});
                coords.putIfAbsent(k2, new double[]{seg.p2X(), seg.p2Z()});
            }
        String sk = sx + "," + sy, ek = ex + "," + ey;
        var gScore = new HashMap<String, Double>();
        var from = new HashMap<String, Edge>();
        var seen = new HashSet<String>();
        var pq = new PriorityQueue<Node>(Comparator.comparingDouble(Node::f));
        gScore.put(sk, 0.0);
        pq.add(new Node(Math.hypot(sx - ex, sy - ey), 0, sk));
        while (!pq.isEmpty()) {
            var cur = pq.poll();
            if (cur.key().equals(ek)) break;
            if (!seen.add(cur.key())) continue;
            for (var e : adj.getOrDefault(cur.key(), List.of())) {
                if (seen.contains(e.to())) continue;
                double ng = cur.g() + e.dist();
                if (ng < gScore.getOrDefault(e.to(), Double.MAX_VALUE)) {
                    gScore.put(e.to(), ng);
                    from.put(e.to(), e);
                    var c = coords.get(e.to());
                    pq.add(new Node(ng + Math.hypot(c[0] - ex, c[1] - ey), ng, e.to()));
                }
            }
        }
        var pathEdges = new ArrayList<Edge>();
        for (String c = ek; from.containsKey(c); c = from.get(c).from())
            pathEdges.add(0, from.get(c));
        var result = new ArrayList<Road>();
        if (Math.hypot(startX - sx, startY - sy) > 1e-6)
            result.add(new Road("open nether", false, List.of(new RoadSegment(startX, startY, sx, sy))));
        int pr = -1;
        List<RoadSegment> cur = null;
        for (var e : pathEdges) {
            if (e.road() != pr) {
                if (cur != null) result.add(new Road(split.get(pr).name(), split.get(pr).paved(), cur));
                cur = new ArrayList<>();
                pr = e.road();
            }
            var seg = split.get(e.road()).segments().get(e.seg());
            if (!nodeKey(seg.p1X(), seg.p1Z()).equals(e.from()))
                seg = new RoadSegment(seg.p2X(), seg.p2Z(), seg.p1X(), seg.p1Z());
            cur.add(seg);
        }
        if (cur != null) result.add(new Road(split.get(pr).name(), split.get(pr).paved(), cur));
        if (Math.hypot(endX - ex, endY - ey) > 1e-6)
            result.add(new Road("open nether", false, List.of(new RoadSegment(ex, ey, endX, endY))));
        return result;
    }

    private static double[] nearestPoint(double px, double py, List<Road> network) {
        double bestDist = Double.MAX_VALUE;
        double[] best = {px, py};
        for (var road : network)
            for (var seg : road.segments()) {
                double t = projectT(px, py, seg);
                double sx = lerp(seg.p1X(), seg.p2X(), t), sy = lerp(seg.p1Z(), seg.p2Z(), t);
                double d = Math.hypot(px - sx, py - sy);
                if (d < bestDist) { bestDist = d; best = new double[]{sx, sy}; }
            }
        return best;
    }

    private static String nodeKey(double x, double y) {
        return Math.round(x) + "," + Math.round(y);
    }

    private static String gridName(RoadSegment seg) {
        if (Math.abs(seg.p1X() - seg.p2X()) < 1e-6) {
            long v = Math.round(seg.p1X());
            return (v >= 0 ? "+" : "") + v + " vertical grid";
        }
        long v = Math.round(seg.p1Z());
        return (v >= 0 ? "+" : "") + v + " horizontal grid";
    }

    public static String heading(Road road) {
        var first = road.segments().get(0);
        var last = road.segments().get(road.segments().size() - 1);
        double dx = last.p2X() - first.p1X(), dy = last.p2Z() - first.p1Z();
        boolean n = dy < -1e-6, s = dy > 1e-6, e = dx > 1e-6, w = dx < -1e-6;
        return n && e ? "northeast" : n && w ? "northwest" : s && e ? "southeast" : s && w ? "southwest"
                : n ? "north" : s ? "south" : e ? "east" : w ? "west" : "???";
    }

    public static String describeTurn(Road from, Road to) {
        var last = from.segments().get(from.segments().size() - 1);
        var first = to.segments().get(0);
        double dx1 = last.p2X() - last.p1X(), dy1 = last.p2Z() - last.p1Z();
        double dx2 = first.p2X() - first.p1X(), dy2 = first.p2Z() - first.p1Z();
        double cross = dx1 * dy2 - dy1 * dx2;
        double dot = dx1 * dx2 + dy1 * dy2;
        double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot / (Math.hypot(dx1, dy1) * Math.hypot(dx2, dy2))))));
        if (angle < 30) return null;
        String side = cross < 0 ? "left" : "right";
        String severity = angle < 60 ? "slight " : angle > 120 ? "sharp " : "";
        return "Make a " + severity + side + " turn on to the " + to.name();
    }

    public static void ensureNetwork(String jsonUrl) throws Exception {
        if (cachedNetwork == null)
            cachedNetwork = snapToGrid(renameRoads(subdivideSegments(fetchRoads(jsonUrl))));
    }

    public static List<Road> calculateRoute(double startX, double startZ, double endX, double endZ) throws Exception {
        ensureNetwork("https://www.desmos.com/calc-states/production/kthhplyigl?cb20221031");
        currentPath = mergeCollinear(findPath(startX, startZ, endX, endZ, cachedNetwork));
        currentSteps.clear();
        currentDirections.clear();
        currentRoadNames.clear();
        for (int i = 0; i < currentPath.size(); i++) {
            var road = currentPath.get(i);
            for (var seg : road.segments()) {
                currentSteps.add(new double[]{seg.p1X(), seg.p1Z()});
                currentRoadNames.add(road.name());
            }
            if (i == currentPath.size() - 1) {
                var last = road.segments().get(road.segments().size() - 1);
                currentSteps.add(new double[]{last.p2X(), last.p2Z()});
                currentRoadNames.add("destination");
            }
            double dist = road.segments().stream().mapToDouble(s -> Math.hypot(s.p2X() - s.p1X(), s.p2Z() - s.p1Z())).sum();
            if (road.name().equals("open nether"))
                currentDirections.add("Fly " + Math.round(dist) + " blocks " + heading(road) + " through open nether");
            else
                currentDirections.add("Continue " + Math.round(dist) + " blocks " + heading(road) + " on the " + road.name());
            if (i < currentPath.size() - 1) {
                String turn = describeTurn(road, currentPath.get(i + 1));
                if (turn != null) currentDirections.add(turn);
            }
        }
        return currentPath;
    }

    public static List<double[]> getSteps() { return currentSteps; }
    public static void clearSteps() { currentSteps.clear(); }
    public static List<String> getDirections() { return currentDirections; }
    public static String getClipboardString() { return currentSteps.stream().map(p -> "(" + Math.round(p[0]) + "," + Math.round(p[1]) + ")").collect(Collectors.joining(", ")); }

    public static double parseDistance(String input) {
        input = input.trim().toLowerCase();
        double mul = 1;
        if (input.endsWith("k")) { mul = 1_000; input = input.substring(0, input.length() - 1); }
        else if (input.endsWith("m")) { mul = 1_000_000; input = input.substring(0, input.length() - 1); }
        else if (input.endsWith("r")) { input = String.valueOf((Math.random() * 2 - 1) * parseDistance(input.substring(0, input.length() - 1))); }
        return Double.parseDouble(input) * mul;
    }

}