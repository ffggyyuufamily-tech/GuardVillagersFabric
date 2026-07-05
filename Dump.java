import net.minecraft.entity.ai.pathing.Path;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
public class Dump {
    public static void main(String[] args) throws Exception {
        for (Constructor<?> c : Path.class.getDeclaredConstructors()) {
            System.out.println(c);
        }
        for (Method m : Path.class.getDeclaredMethods()) {
            System.out.println(m);
        }
    }
}
