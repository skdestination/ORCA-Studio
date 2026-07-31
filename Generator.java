import com.litecut.app.timeline.TimelineEngine;
public class Generator {
    public static void main(String[] args) {
        TimelineEngine engine = new TimelineEngine();
        System.out.println(engine.getProjectJSON().toString());
    }
}
