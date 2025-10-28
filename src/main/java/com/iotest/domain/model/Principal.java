import java.net.http.HttpClient;

public class Principal {
    public static void main(String[] args) {
        HttpClient client = HttpClient.newHttpClient();

        ISwitchController ISC = new SwitchController();

        String SwitchURL = "http://host:port/switch/1";
        String SwitchStatus = ISC.getSwitchStatus(SwitchURL);
        System.out.println(SwitchStatus);
    }
}