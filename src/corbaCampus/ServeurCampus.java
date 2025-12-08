package corbaCampus;

import Campus.*;
import org.omg.CORBA.*;
import org.omg.PortableServer.*;
import javax.naming.*;
import javax.naming.Context;
import java.util.Properties;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;

public class ServeurCampus {
    private static ServerSocket tcpServerSocket;
    private static ExecutorService clientThreadPool;
    private static final ConcurrentHashMap<String, PrintWriter> clients = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        try {
            // 1. Initialisation ORB CORBA
            ORB orb = ORB.init(args, null);
            System.out.println("✓ ORB CORBA initialisé");

            // 2. Création POA pour gérer les objets servants
            POA rootpoa = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
            rootpoa.the_POAManager().activate();
            System.out.println("✓ POA activé");

            // 3. Instanciation de l'implémentation (servant CORBA)
            GestionCampusImpl impl = new GestionCampusImpl();
            System.out.println("✓ Implémentation GestionCampus créée");

            // 4. Conversion servant en référence CORBA
            org.omg.CORBA.Object ref = rootpoa.servant_to_reference(impl);
            System.out.println("✓ Référence CORBA générée");

            // 5. Configuration JNDI pour CORBA
            Properties jndiProps = new Properties();
            jndiProps.put(Context.INITIAL_CONTEXT_FACTORY,
                    "com.sun.jndi.cosnaming.CNCtxFactory");
            jndiProps.put(Context.PROVIDER_URL,
                    "iiop://localhost:1050");

            // 6. Création du contexte JNDI
            Context ctx = new InitialContext(jndiProps);
            System.out.println("✓ Contexte JNDI créé");

            // 7. Publication de l'objet CORBA via JNDI
            ctx.rebind("GestionCampus", ref);

            // 8. Démarrer le serveur TCP pour les notifications
            demarrerServeurTCP();

            System.out.println("========================================");
            System.out.println(" Serveur Campus prêt !");
            System.out.println(" Objet 'GestionCampus' publié via JNDI");
            System.out.println(" URL CORBA: iiop://localhost:1050");
            System.out.println(" Nom JNDI: GestionCampus");
            System.out.println(" Serveur TCP démarré sur le port 9999");
            System.out.println(" En attente des clients...\n");

            // 9. Envoyer une notification de démarrage
            broadcastNotification("SERVER_STARTED:Serveur Campus démarré avec succès");

            // 10. CORBA orb.run() garde le serveur vivant pour les appels distants
            orb.run();

        } catch (NamingException e) {
            System.err.println("ERREUR JNDI : " + e.getMessage());
            System.err.println("Vérifiez que orbd est lancé avec : orbd -ORBInitialPort 1050");
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("ERREUR LANCEMENT SERVEUR CORBA : " + e.getMessage());
            e.printStackTrace();
        } finally {
            arreterServeurTCP();
        }
    }

    //Démarrer le serveur TCP pour les notifications

    private static void demarrerServeurTCP() {
        try {
            tcpServerSocket = new ServerSocket(9999);
            clientThreadPool = Executors.newCachedThreadPool();
            System.out.println("📡 Serveur TCP démarré sur le port 9999");

            // Thread pour accepter les connexions clients
            Thread acceptThread = new Thread(() -> {
                try {
                    while (!tcpServerSocket.isClosed()) {
                        Socket clientSocket = tcpServerSocket.accept();
                        String clientId = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
                        System.out.println("Client connecté: " + clientId);

                        clientThreadPool.execute(new ClientHandler(clientSocket, clientId));
                    }
                } catch (IOException e) {
                    if (!tcpServerSocket.isClosed()) {
                        System.err.println(" Erreur d'acceptation TCP: " + e.getMessage());
                    }
                }
            });

            acceptThread.setDaemon(true);
            acceptThread.start();

        } catch (IOException e) {
            System.err.println("Impossible de démarrer le serveur TCP: " + e.getMessage());
        }
    }

    //Arrêter le serveur TCP
    private static void arreterServeurTCP() {
        try {
            if (tcpServerSocket != null && !tcpServerSocket.isClosed()) {
                tcpServerSocket.close();
            }
            if (clientThreadPool != null) {
                clientThreadPool.shutdown();
            }
            System.out.println("Serveur TCP arrêté");
        } catch (IOException e) {
            System.err.println("Erreur lors de l'arrêt du serveur TCP: " + e.getMessage());
        }
    }

    //Diffuser une notification à tous les clients

    public static void broadcastNotification(String message) {
        System.out.println("Notification broadcast: " + message);
        for (PrintWriter out : clients.values()) {
            try {
                out.println(message);
                out.flush();
            } catch (Exception e) {
                System.err.println("Erreur d'envoi à un client: " + e.getMessage());
            }
        }
    }

    //Classe pour gérer les clients TCP

    private static class ClientHandler implements Runnable {
        private Socket socket;
        private String clientId;
        private PrintWriter out;
        private BufferedReader in;

        public ClientHandler(Socket socket, String clientId) {
            this.socket = socket;
            this.clientId = clientId;
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // Ajouter le client à la liste
                clients.put(clientId, out);

                // Envoyer un message de bienvenue
                out.println("BIENVENUE:Connecté au serveur de notifications Campus");

                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    System.out.println("Message de " + clientId + ": " + inputLine);

                    // Diffuser les messages aux autres clients
                    if (inputLine.startsWith("CLIENT_CONNECTE:")) {
                        broadcastNotification("CLIENT_ARRIVE:" + inputLine.substring(15));
                    } else if (!inputLine.startsWith("TEST:")) {
                        broadcastNotification("CLIENT_MSG:" + clientId + ":" + inputLine);
                    }
                }
            } catch (IOException e) {
                System.err.println("Erreur avec le client " + clientId + ": " + e.getMessage());
            } finally {
                // Nettoyer
                clients.remove(clientId);
                try {
                    socket.close();
                } catch (IOException e) {
                    System.err.println("Erreur de fermeture socket: " + e.getMessage());
                }
                System.out.println("Client  déconnecté: " + clientId);
            }
        }
    }
}