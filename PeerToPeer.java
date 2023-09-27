import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class PeerToPeer {

    static final int PORT = 2425;
    static final int TCP_PORT = 12456;
    static final int ECHO = 0;
    static final int REPLY_TO_ECHO = 1;
    static final int REQUEST_FILE = 2;
    static final int ACCEPT_REQUEST = 3;
    static final int DENY_REQUEST = 4;
    static boolean waitReq = false;
    static boolean requestRes = false;
    private static final Object lock = new Object();
    static ReentrantLock tlock = new ReentrantLock(true);
    static BufferedReader br;
    static String UID;
    static String selfIP;
    static String localHost = "127.0.0.1";
    static DatagramSocket dSocket;
    static HashMap<String, String> idToIp = new HashMap<>();

    public static void main(String[] args) throws Exception {
        br = new BufferedReader(new InputStreamReader(System.in));

        initialize();
        broadcastPresence();
        listenToPings();

        while (true) {
            System.out.println("Enter Options:");
            System.out.println("1. List all neighbours");
            System.out.println("2. Send file");
            int opt = Integer.parseInt(br.readLine());
            if (opt == 1) {
                for (String key : idToIp.keySet()) {
                    System.out.println(key + " : " + idToIp.get(key));
                }
            } else if (opt == 2) {

                System.out.println("Choose the receiver:");
                String receiver = br.readLine();

                System.out.println("Sending Request...");

                sendRequest(receiver);

                System.out.println("Enter file path");
                String path = br.readLine();

                Socket socket = new Socket(idToIp.get(receiver), TCP_PORT);

                File file = new File(path);

                // System.out.println("Enter thr message:");
                FileInputStream fis = new FileInputStream(file);
                BufferedInputStream bis = new BufferedInputStream(fis);
                // Get socket's output stream
                OutputStream os = socket.getOutputStream();
                // Read File Contents into contents array
                byte[] contents;
                long fileLength = file.length();
                long current = 0;
                long start = System.nanoTime();
                while (current != fileLength) {
                    int size = 10000;
                    if (fileLength - current >= size)
                        current += size;
                    else {
                        size = (int) (fileLength - current);
                        current = fileLength;
                    }
                    contents = new byte[size];
                    bis.read(contents, 0, size);
                    os.write(contents);
                    System.out.print("Sending file ... " + (current * 100) / fileLength + "% complete!");
                }
                os.flush();
                socket.close();
                System.out.println("File sent succesfully!\n");
            }
        }

    }

    public static void initialize() {
        try {
            generateUID();
            InetAddress selfAddress = InetAddress.getLocalHost();
            selfIP = selfAddress.getHostAddress();
            dSocket = new DatagramSocket(PORT);
            Runtime.getRuntime().addShutdownHook(new Thread(new ShutDownHook()));
            System.out.println("#DEBUG: YOUR IP: " + selfIP);
        } catch (Exception e) {
            System.out.println("Err : " + e.getMessage());
        }
    }

    public static void generateUID() {
        UUID uid = UUID.randomUUID();
        UID = uid.toString();
        System.out.println("#DEBUG: YOUR UID: " + UID);
    }

    public static void sendRequest(String receiver) {
        try {
            String msg = REQUEST_FILE + "#" + UID;

            byte[] sendData = msg.getBytes();

            InetAddress broadcastAddress = InetAddress.getByName(idToIp.get(receiver));

            DatagramPacket sendPacket = new DatagramPacket(
                    sendData,
                    sendData.length,
                    broadcastAddress,
                    PORT);

            getSocket().send(sendPacket);
        } catch (Exception e) {
            System.out.println("#Something went wrong(method: sendRequest()):  " + e.getMessage());
        }
    }

    public static DatagramSocket getSocket() {
        synchronized (lock) {
            return dSocket;
        }

    }

    public static void broadcastPresence() throws Exception {
        new Thread(new Runnable() {
            public void run() {
                try {
                    // DatagramSocket dSocket = new DatagramSocket(PORT);
                    getSocket().setBroadcast(true);

                    String msg = ECHO + "#" + UID;
                    byte[] sendData = msg.getBytes();

                    InetAddress broadcastAddress = InetAddress.getByName("255.255.255.255");

                    DatagramPacket sendPacket = new DatagramPacket(
                            sendData,
                            sendData.length,
                            broadcastAddress,
                            PORT);

                    getSocket().send(sendPacket);
                } catch (Exception e) {
                    System.err.println("#Something went wrong(method: broadcastPresence()): " + e.getMessage());
                }
            }
        }).start();
    }

    public static void listenToPings() {
        try {
            Thread listenerThread = new Thread(new PingListener());
            listenerThread.start();
        } catch (Exception e) {
            System.out.println("#Something went wrong(method: listenToPings()): " + e);
        }
    }

    static class PingListener implements Runnable {

        public PingListener() {

        }

        @Override
        public void run() {
            try {
                // DatagramSocket socket = new DatagramSocket(PORT);

                // ExecutorService executorService = Executors.newFixedThreadPool(10);

                while (true) {
                    byte[] buffer = new byte[2048];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                    // synchronized (lock) {
                    // dSocket.receive(packet);
                    // }
                    getSocket().receive(packet);
                    System.out.println("check1-");
                    // executorService.submit(new PacketHandler(dSocket, packet));
                    new Thread(new PacketHandler(packet)).start();
                }
            } catch (Exception e) {
                System.err.println("#Something wrong happened: " + e.getMessage());
            }
        }
    }

    static public class PacketHandler implements Runnable {
        // private DatagramSocket socket;
        private DatagramPacket packet;

        public PacketHandler(DatagramPacket packet) {
            // this.socket = socket;
            this.packet = packet;
        }

        @Override
        public void run() {
            try {
                System.out.println("Check2");
                String data = new String(packet.getData(), 0, packet.getLength());

                StringTokenizer st = new StringTokenizer(data, "#");

                int count = st.countTokens();

                if (count > 1) {
                    int msgType = Integer.parseInt(st.nextToken());
                    String id = st.nextToken();

                    InetAddress clientAddress = packet.getAddress();
                    // System.out.println(clientAddress.toString());

                    // idToIp.put(id, clientAddress.toString());
                    if (msgType == ECHO && !selfIP.equals(clientAddress.getHostAddress())
                            && !selfIP.equals(localHost)) {
                        idToIp.put(id, clientAddress.getHostAddress());
                        String msg = REPLY_TO_ECHO + "#" + UID;
                        byte[] msgToByte = msg.getBytes();
                        packet = new DatagramPacket(
                                msgToByte,
                                msgToByte.length,
                                clientAddress,
                                PORT);

                        getSocket().send(packet);
                    } else if (msgType == REPLY_TO_ECHO && !selfIP.equals(clientAddress.toString())
                            && !selfIP.equals(localHost)) {
                        idToIp.put(id, clientAddress.getHostAddress());
                    } else if (msgType == REQUEST_FILE && !selfIP.equals(clientAddress.toString())) {
                        tlock.lock();
                        try {
                            getSocket().close();
                            ServerSocket serverSocket = new ServerSocket(TCP_PORT);
                            Socket sock = serverSocket.accept();
                            byte[] contents = new byte[10000];
                            FileOutputStream fos = new FileOutputStream("D:\\file-" + id + ".txt");
                            BufferedOutputStream bos = new BufferedOutputStream(fos);
                            InputStream is = sock.getInputStream();
                            // No of bytes read in one read() call
                            int bytesRead = 0;
                            while ((bytesRead = is.read(contents)) != -1)
                                bos.write(contents, 0, bytesRead);
                            bos.flush();
                            sock.close();
                            serverSocket.close();
                            dSocket = new DatagramSocket(PORT);
                            System.out.println("File saved successfully!");
                        } finally {
                            tlock.unlock();
                        }
                    }
                } else {
                    String msg = st.nextToken();
                    System.out.println(msg);
                }

            } catch (Exception e) {
                System.err.println("$Something wrong happened: " + e.getMessage());
            }
        }
    }

    static public class ShutDownHook implements Runnable {

        @Override
        public void run() {
            // TODO Auto-generated method stub
            // throw new UnsupportedOperationException("Unimplemented method 'run'");
            System.out.println("#DEBUG : SHUTDOWN HOOK CHECK");
            dSocket.close();
        }

    }

    static public class Node {
        int port;
        String ip;

        public Node(int port, String ip) {

        }
    }
}