import java.io.IOException;

public interface ISwitchController {
public String getSwitchStatus(String SwitchURL) throws IOException, InterruptedException;
public  String postSwitchStatus(String SwitchURL, boolean estadoDeseado) throws IOException, InterruptedException;
}