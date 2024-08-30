import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server implements Runnable {
    private ArrayList<ConnectionHandler> connections;
    private HashMap<String, ConnectionHandler> nicknameMap;
    private ServerSocket server;
    private boolean done;
    private ExecutorService pool;
    private int porta = 3000;

    public Server() {
        connections = new ArrayList<>();
        nicknameMap = new HashMap<>();
        done = false;
    }

    @Override
    public void run() {
        try {
            server = new ServerSocket(porta);
            pool = Executors.newCachedThreadPool();
            System.out.println("Servidor rodando na porta " + porta);

            while (!done) {
                Socket client = server.accept();
                ConnectionHandler handler = new ConnectionHandler(client);
                pool.execute(handler);
            }
        } catch (IOException e) {
            shutdown();
        }
    }

    public void broadcast(String message) {
        for (ConnectionHandler ch : connections) {
            if (ch != null) {
                ch.sendMessage(message);
            }
        }
    }

    public void shutdown() {
        try {
            done = true;
            if (!server.isClosed()) {
                server.close();
            }
            for (ConnectionHandler ch : connections) {
                ch.shutdown();
            }
            pool.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    class ConnectionHandler implements Runnable {
        private Socket client;
        private BufferedReader in;
        private PrintWriter out;
        private String nickname;
        private boolean loggedIn = false;

        public ConnectionHandler(Socket client) {
            this.client = client;
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(client.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(client.getInputStream()));

                // Exige que o cliente forneça o nickname
                String loginMessage = in.readLine();
                if (loginMessage != null && loginMessage.startsWith("!nick ")) {
                    String[] messageSplit = loginMessage.split(" ", 2);
                    if (messageSplit.length == 2 && !messageSplit[1].trim().isEmpty()) {
                        nickname = messageSplit[1].trim();
                        synchronized (connections) {
                            if (nicknameMap.containsKey(nickname)) {
                                out.println("Nickname já em uso.");
                                shutdown();
                                return;
                            }
                            loggedIn = true;
                            connections.add(this);
                            nicknameMap.put(nickname, this);
                            out.println("Bem-vindo, " + nickname);
                            broadcast("!users " + nicknameMap.keySet());
                            broadcast(nickname + " entrou no chat!");
                        }
                    } else {
                        out.println("Formato inválido! Use: !nick [seu_nome]");
                        shutdown();
                    }
                } else {
                    out.println("Você precisa se logar com: !nick [seu_nome]");
                    shutdown();
                }

                String message;
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("!sendmsg ")) {
                        String msgContent = message.substring(9).trim();
                        broadcast("!msg " + nickname + ": " + msgContent);
                    } else if (message.startsWith("!changenickname ")) {
                        String[] messageSplit = message.split(" ", 2);
                        if (messageSplit.length == 2) {
                            String oldNickname = nickname;
                            String newNickname = messageSplit[1];
                            if (nicknameMap.containsKey(newNickname)) {
                                out.println("O nickname já está em uso.");
                            } else {
                                synchronized (connections) {
                                    nicknameMap.remove(oldNickname);
                                    nicknameMap.put(newNickname, this);
                                    nickname = newNickname;
                                    broadcast("!changenickname " + oldNickname + " " + newNickname);
                                }
                            }
                        }
                    } else if (message.startsWith("!poke ")) {
                        String[] messageSplit = message.split(" ", 2);
                        if (messageSplit.length == 2) {
                            String targetNickname = messageSplit[1];
                            ConnectionHandler target = nicknameMap.get(targetNickname);
                            if (target != null) {
                                broadcast("!poke " + nickname + " poked " + targetNickname);
                            } else {
                                out.println("Usuário não encontrado.");
                            }
                        }
                    }
                }
            } catch (IOException e) {
                shutdown();
            }
        }

        public void sendMessage(String message) {
            out.println(message);
        }

        public void shutdown() {
            try {
                loggedIn = false;
                synchronized (connections) {
                    connections.remove(this);
                    nicknameMap.remove(nickname);
                    broadcast(nickname + " saiu do chat.");
                }
                in.close();
                out.close();
                if (!client.isClosed()) {
                    client.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        Server server = new Server();
        server.run();
    }
}
