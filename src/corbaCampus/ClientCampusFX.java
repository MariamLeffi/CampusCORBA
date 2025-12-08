package corbaCampus;
import Campus.*;
import org.omg.CORBA.*;
import javax.naming.*;
import javax.naming.Context;
import java.lang.Object;
import java.util.Properties;
import javafx.application.Application;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.animation.*;
import javafx.util.Duration;
import java.net.*;
import java.io.*;
import java.time.LocalDateTime;
import javafx.scene.shape.Rectangle;

public class ClientCampusFX extends Application {
    private GestionCampus gestion;
    private TabPane tabPane;
    private Tab notificationsTab;
    // TCP pour les notifications
    private Socket tcpSocket;
    private PrintWriter tcpOut;
    private BufferedReader tcpIn;
    private boolean tcpRunning = true;
    private Thread tcpListenerThread;
    // Carte du campus
    private GridPane campusMap = new GridPane();
    private ScrollPane mapScrollPane;
    private Tab mapTab;
    // Chambres
    private TableView<Chambre> tableChambres = new TableView<>();
    private TextField tfNum, tfCapacite, tfPrix, tfEquipements, tfDescription;
    private ComboBox<String> cbTypeChambre, cbStatutChambre;
    private ObservableList<Chambre> chambresList = FXCollections.observableArrayList();
    // Clients
    private TableView<Client> tableClients = new TableView<>();
    private TextField tfNom, tfEmail, tfTel, tfOrganisation;
    private ComboBox<String> cbTypeClient;
    private ObservableList<Client> clientsList = FXCollections.observableArrayList();
    // Reservations
    private TableView<Reservation> tableReservations = new TableView<>();
    private ComboBox<String> cbClientResa, cbChambreResa, cbStatutResa;
    private DatePicker dpDateDebut, dpDateFin;
    private ObservableList<Reservation> reservationsList = FXCollections.observableArrayList();
    // Notifications
    private TextArea taNotifications = new TextArea();
    private int notificationCount = 0;
    private ListView<String> lvNotifications = new ListView<>();
    private ObservableList<String> notificationsList = FXCollections.observableArrayList();

    @Override
    public void start(Stage primaryStage) {
        try {
            connecterCORBA();
            demarrerTCPListener(); // Démarrer l'écoute TCP
            // En-tête avec titre et logo
            VBox header = createHeader();
            tabPane = new TabPane();
            // Créer les onglets
            Tab tabChambres = createTab("🏠 Chambres", panelChambres());
            Tab tabClients = createTab("👤 Clients", panelClients());
            Tab tabReservations = createTab("📆 Réservations", panelReservations());
            notificationsTab = createTab("🔔 Notifications", panelNotifications());
            mapTab = createTab("Carte du Campus", panelCarteCampus());
            tabPane.getTabs().addAll(tabChambres, tabClients, tabReservations, mapTab, notificationsTab);
            tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
            VBox root = new VBox(header, tabPane);
            root.setStyle("-fx-background-color: linear-gradient(to bottom, #667eea 0%, #764ba2 100%);");
            Scene scene = new Scene(root, 1400, 900);
            scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
            primaryStage.setTitle("Gestion de Campus - Système de Réservations (CORBA + TCP + Carte Interactive)");
            primaryStage.setScene(scene);
            primaryStage.show();
            // Animation d'entrée
            fadeIn(root);
            // Ajouter le message de bienvenue
            ajouterNotification("=== Système de Gestion de Campus ===");
            ajouterNotification("Connecté au serveur CORBA via JNDI");
            ajouterNotification("Heure de connexion: " + LocalDateTime.now().toString());
            ajouterNotification("Connexion TCP établie sur le port 9999");
            ajouterNotification("En attente de notifications en temps réel...");
            // Charger toutes les données
            chargerChambres();
            chargerClients();
            chargerToutesReservations();
            actualiserCarteCampus();
        } catch (Exception e) {
            showError("Erreur lors du démarrage: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private VBox createHeader() {
        VBox header = new VBox(10);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(20, 0, 15, 0));
        header.setStyle("-fx-background-color: rgba(0,0,0,0.2);");
        Label titre = new Label("🏛 GESTION DE CAMPUS");
        titre.setFont(Font.font("Segoe UI", FontWeight.BOLD, 36));
        titre.setTextFill(Color.WHITE);
        titre.setEffect(new DropShadow(15, Color.BLACK));
        Label sousTitre = new Label("Système de Réservation - Architecture CORBA(JNDI) avec TCP ");
        sousTitre.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 16));
        sousTitre.setTextFill(Color.web("#E0E0E0"));
        // Animation pulsation du titre
        ScaleTransition pulse = new ScaleTransition(Duration.seconds(2), titre);
        pulse.setFromX(1.0);
        pulse.setFromY(1.0);
        pulse.setToX(1.05);
        pulse.setToY(1.05);
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.setAutoReverse(true);
        pulse.play();
        header.getChildren().addAll(titre, sousTitre);
        return header;
    }

    private Tab createTab(String title, Pane content) {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    private void fadeIn(VBox root) {
        FadeTransition fade = new FadeTransition(Duration.millis(800), root);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    /**
     * CONNEXION CORBA VIA JNDI
     */
    private void connecterCORBA() {
        try {
            System.out.println("Connexion au serveur CORBA via JNDI...");
            // 1. Initialisation de l'ORB
            ORB orb = ORB.init(new String[0], null);
            System.out.println("✓ ORB initialisé");
            // 2. Configuration des propriétés JNDI pour CORBA
            Properties jndiProps = new Properties();
            jndiProps.put(Context.INITIAL_CONTEXT_FACTORY,
                    "com.sun.jndi.cosnaming.CNCtxFactory");
            jndiProps.put(Context.PROVIDER_URL,
                    "corbaname::localhost:1050");
            System.out.println("Configuration JNDI:");
            System.out.println(" - Factory: com.sun.jndi.cosnaming.CNCtxFactory");
            System.out.println(" - URL: corbaname::localhost:1050");
            // 3. Création du contexte JNDI
            Context ctx = new InitialContext(jndiProps);
            System.out.println("✓ Contexte JNDI créé");
            // 4. Lookup de l'objet CORBA via JNDI
            Object obj = ctx.lookup("GestionCampus");
            System.out.println("✓ Objet 'GestionCampus' trouvé dans JNDI");
            // 5. Narrowing vers l'interface GestionCampus
            gestion = GestionCampusHelper.narrow((org.omg.CORBA.Object) obj);
            System.out.println("✓ Narrowing réussi vers GestionCampus");
            System.out.println("Connexion CORBA via JNDI réussie !");
            System.out.println("Appels distants activés");
        } catch (NamingException e) {
            String errorMsg = "ERREUR JNDI : Impossible de contacter le serveur !\n\n";
            errorMsg += "Causes possibles :\n";
            errorMsg += "1. Le service orbd n'est pas lancé\n";
            errorMsg += " → Lancez : orbd -ORBInitialPort 1050\n\n";
            errorMsg += "2. Le serveur CORBA n'est pas démarré\n";
            errorMsg += " → Lancez ServeurCampus.java\n\n";
            errorMsg += "3. Mauvaise configuration du port\n";
            errorMsg += " → Vérifiez que orbd écoute sur le port 1050\n\n";
            errorMsg += "Détails technique : " + e.getMessage();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Erreur de connexion JNDI");
            alert.setHeaderText("Impossible de contacter le serveur CORBA");
            alert.setContentText(errorMsg);
            alert.showAndWait();
            System.err.println(errorMsg);
            e.printStackTrace();
            System.exit(1);
        } catch (Exception e) {
            String errorMsg = "ERREUR : " + e.getMessage();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Erreur de connexion");
            alert.setHeaderText("Erreur lors de la connexion CORBA");
            alert.setContentText(errorMsg);
            alert.showAndWait();
            System.err.println(errorMsg);
            e.printStackTrace();
            System.exit(1);
        }
    }

    //TCP - Écoute des notifications

    private void demarrerTCPListener() {
        try {
            tcpSocket = new Socket("localhost", 9999);
            tcpOut = new PrintWriter(tcpSocket.getOutputStream(), true);
            tcpIn = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream()));
            System.out.println("Connexion TCP établie sur le port 9999");
            tcpOut.println("CLIENT_CONNECTE:" + InetAddress.getLocalHost().getHostName());
            tcpListenerThread = new Thread(() -> {
                try {
                    String message;
                    while (tcpRunning && (message = tcpIn.readLine()) != null) {
                        final String msg = message;
                        javafx.application.Platform.runLater(() -> {
                            ajouterNotification("📡 TCP: " + msg);
                        });
                        System.out.println("Notification TCP reçue: " + msg);
                    }
                } catch (SocketException e) {
                    if (tcpRunning) {
                        System.err.println("Erreur socket TCP: " + e.getMessage());
                    }
                } catch (Exception e) {
                    System.err.println("Erreur dans le listener TCP: " + e.getMessage());
                }
            });
            tcpListenerThread.setDaemon(true);
            tcpListenerThread.start();
        } catch (Exception e) {
            System.err.println("Impossible de se connecter au serveur TCP: " + e.getMessage());
            ajouterNotification("⚠Impossible de se connecter aux notifications TCP");
        }
    }

    //TCP - Envoyer une notification

    private void envoyerNotificationTCP(String message) {
        try {
            if (tcpOut != null) {
                tcpOut.println(message);
                System.out.println("Notification TCP envoyée: " + message);
            }
        } catch (Exception e) {
            System.err.println("Erreur lors de l'envoi TCP: " + e.getMessage());
        }
    }

    @Override
    public void stop() {
        tcpRunning = false;
        try {
            if (tcpSocket != null) {
                tcpSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Application fermée");
    }

    // ===================== PANEL CARTE DU CAMPUS =====================
    private VBox panelCarteCampus() {
        VBox vbox = new VBox(15);
        vbox.setPadding(new Insets(20));
        vbox.setStyle("-fx-background-color: rgba(255,255,255,0.95); -fx-background-radius: 20;");
        Label titleLabel = new Label("Carte Interactive du Campus");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 24));
        titleLabel.setTextFill(Color.web("#667eea"));
        // Légende
        HBox legendBox = new HBox(20);
        legendBox.setAlignment(Pos.CENTER);
        legendBox.setPadding(new Insets(10, 0, 20, 0));
        VBox legDispo = createLegendBox("Disponible", "#4CAF50");
        VBox legOccupee = createLegendBox("Occupée", "#F44336");
        VBox legReservee = createLegendBox("Réservée", "#2196F3");
        VBox legMaintenance = createLegendBox("Maintenance", "#FF9800");
        legendBox.getChildren().addAll(legDispo, legOccupee, legReservee, legMaintenance);
        // Configuration de la carte
        campusMap.setPadding(new Insets(20));
        campusMap.setHgap(15);
        campusMap.setVgap(15);
        campusMap.setAlignment(Pos.CENTER);
        // ScrollPane pour la carte
        mapScrollPane = new ScrollPane(campusMap);
        mapScrollPane.setFitToWidth(true);
        mapScrollPane.setFitToHeight(true);
        mapScrollPane.setPrefHeight(600);
        mapScrollPane.setStyle("-fx-background-color: #f5f5f5; -fx-background-radius: 15;");
        HBox buttons = new HBox(12);
        buttons.setAlignment(Pos.CENTER);
        buttons.setPadding(new Insets(15, 0, 0, 0));
        Button btnRefresh = createStyledButton("🔄 Actualiser la carte", "#4facfe", "#00f2fe");
        btnRefresh.setOnAction(e -> actualiserCarteCampus());
        Button btnZoomIn = createStyledButton("🔍+ Zoom", "#11998e", "#38ef7d");
        btnZoomIn.setOnAction(e -> zoomCarte(1.1));
        Button btnZoomOut = createStyledButton("🔍- Zoom", "#ff6b6b", "#ee5a52");
        btnZoomOut.setOnAction(e -> zoomCarte(0.9));
        buttons.getChildren().addAll(btnRefresh, btnZoomIn, btnZoomOut);
        vbox.getChildren().addAll(titleLabel, legendBox, mapScrollPane, buttons);
        VBox.setVgrow(mapScrollPane, Priority.ALWAYS);
        return vbox;
    }

    private VBox createLegendBox(String text, String color) {
        VBox box = new VBox(5);
        box.setAlignment(Pos.CENTER);
        Rectangle rect = new Rectangle(30, 30);
        rect.setStyle("-fx-fill: " + color + "; -fx-stroke: #333; -fx-stroke-width: 1; -fx-arc-width: 5; -fx-arc-height: 5;");
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        box.getChildren().addAll(rect, label);
        return box;
    }

    private void actualiserCarteCampus() {
        try {
            campusMap.getChildren().clear();
            Chambre[] chambres = gestion.listerToutesChambres();
            if (chambres != null && chambres.length > 0) {
                int col = 0;
                int row = 0;
                int maxCols = 8; // Nombre maximum de colonnes
                for (Chambre chambre : chambres) {
                    VBox roomBox = createRoomBox(chambre);
                    campusMap.add(roomBox, col, row);
                    col++;
                    if (col >= maxCols) {
                        col = 0;
                        row++;
                    }
                }
                System.out.println("✓ Carte du campus actualisée: " + chambres.length + " chambres affichées");
                ajouterNotification("🗺️ Carte du campus actualisée avec " + chambres.length + " chambres");
            } else {
                Label noRoomsLabel = new Label("Aucune chambre disponible");
                noRoomsLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #666;");
                campusMap.add(noRoomsLabel, 0, 0);
            }
        } catch (Exception e) {
            System.err.println("Erreur lors de l'actualisation de la carte: " + e.getMessage());
        }
    }

    private VBox createRoomBox(Chambre chambre) {
        VBox roomBox = new VBox(5);
        roomBox.setAlignment(Pos.CENTER);
        roomBox.setPadding(new Insets(15));
        roomBox.setPrefSize(120, 120);
        // Déterminer la classe CSS en fonction du statut
        String styleClass = "";
        switch (chambre.statut) {
            case "Disponible":
                styleClass = "room-disponible";
                break;
            case "Occupée":
                styleClass = "room-occupee";
                break;
            case "Réservée":
                styleClass = "room-reservee";
                break;
            case "Maintenance":
                styleClass = "room-maintenance";
                break;
            default:
                styleClass = "room-disponible";
        }
        roomBox.getStyleClass().addAll("room-box", styleClass);
        // Numéro de chambre
        Label numLabel = new Label("№ " + chambre.numero);
        numLabel.getStyleClass().add("room-label");
        numLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        // Type de chambre
        Label typeLabel = new Label(chambre.type_chambre);
        typeLabel.getStyleClass().add("room-label");
        typeLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 10));
        // Capacité
        Label capLabel = new Label("👥 " + chambre.capacite);
        capLabel.getStyleClass().add("room-label");
        capLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 10));
        // Prix
        Label prixLabel = new Label(String.format("%.0f D", chambre.prix_par_jour));
        prixLabel.getStyleClass().add("room-label");
        prixLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 10));
        roomBox.getChildren().addAll(numLabel, typeLabel, capLabel, prixLabel);
        // Tooltip avec informations détaillées
        Tooltip tooltip = new Tooltip(
                "Chambre: " + chambre.numero + "\n" +
                        "Type: " + chambre.type_chambre + "\n" +
                        "Capacité: " + chambre.capacite + " personnes\n" +
                        "Prix/jour: " + chambre.prix_par_jour + " FCFA\n" +
                        "Statut: " + chambre.statut + "\n" +
                        "Équipements: " + chambre.equipements + "\n" +
                        "Description: " + chambre.description
        );
        tooltip.setStyle("-fx-font-size: 12px;");
        Tooltip.install(roomBox, tooltip);
        // Gestion du clic
        roomBox.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                afficherDetailsChambre(chambre);
            } else {
                // Animation au clic
                ScaleTransition st = new ScaleTransition(Duration.millis(100), roomBox);
                st.setToX(0.95);
                st.setToY(0.95);
                st.setAutoReverse(true);
                st.setCycleCount(2);
                st.play();
                ajouterNotification("📍 Chambre sélectionnée: " + chambre.numero + " (" + chambre.statut + ")");
            }
        });
        return roomBox;
    }

    private void afficherDetailsChambre(Chambre chambre) {
        Alert details = new Alert(Alert.AlertType.INFORMATION);
        details.setTitle("Détails de la Chambre");
        details.setHeaderText("Chambre № " + chambre.numero);
        String content =
                "📋 Numéro: " + chambre.numero + "\n" +
                        "🏷️ Type: " + chambre.type_chambre + "\n" +
                        "👥 Capacité: " + chambre.capacite + " personnes\n" +
                        "💰 Prix/jour: " + String.format("%.0f", chambre.prix_par_jour) + " D\n" +
                        "🔔 Statut: " + chambre.statut + "\n" +
                        "⚙️ Équipements: " + chambre.equipements + "\n" +
                        "📝 Description: " + chambre.description + "\n\n";
        // Ajouter les réservations si existantes
        try {
            Reservation[] reservations = gestion.reservationsChambre((int) chambre.chambre_id);
            if (reservations != null && reservations.length > 0) {
                content += "📆 Réservations:\n";
                for (Reservation r : reservations) {
                    if (!"Annulée".equals(r.statut)) {
                        content += " • " + r.date_debut + " → " + r.date_fin + " (" + r.statut + ")\n";
                    }
                }
            }
        } catch (Exception e) {
            // Ignorer les erreurs de récupération des réservations
        }
        details.setContentText(content);
        // Ajouter un bouton pour réserver
        ButtonType reserverBtn = new ButtonType("Réserver cette chambre");
        details.getButtonTypes().add(reserverBtn);
        details.showAndWait().ifPresent(response -> {
            if (response == reserverBtn) {
                // Basculer vers l'onglet réservations et pré-remplir
                tabPane.getSelectionModel().select(2); // Index de l'onglet réservations
                cbChambreResa.setValue(chambre.chambre_id + " - " + chambre.numero + " [" + chambre.type_chambre + "]");
            }
        });
    }

    private void zoomCarte(double factor) {
        campusMap.setScaleX(campusMap.getScaleX() * factor);
        campusMap.setScaleY(campusMap.getScaleY() * factor);
        ajouterNotification("🔍 Zoom de la carte: " + String.format("%.0f", factor * 100) + "%");
    }

    // ===================== PANEL CHAMBRES =====================
    private VBox panelChambres() {
        VBox vbox = new VBox(15);
        vbox.setPadding(new Insets(20));
        vbox.setStyle("-fx-background-color: rgba(255,255,255,0.95); -fx-background-radius: 20;");
        Label titleLabel = new Label("🏠 Gestion des Chambres");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 24));
        titleLabel.setTextFill(Color.web("#667eea"));
        GridPane form = new GridPane();
        form.setHgap(15);
        form.setVgap(12);
        form.setPadding(new Insets(15));
        form.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 15; -fx-padding: 20;");
        tfNum = createStyledTextField("ex: A101");
        cbTypeChambre = new ComboBox<>();
        cbTypeChambre.getItems().addAll("Simple", "Double", "Triple", "Suite", "Dortoir");
        cbTypeChambre.setPromptText("Choisir le type");
        cbTypeChambre.setPrefWidth(250);
        tfCapacite = createStyledTextField("ex: 2");
        tfPrix = createStyledTextField("ex: 250");
        tfEquipements = createStyledTextField("ex: WiFi, TV, Climatisation");
        tfDescription = createStyledTextField("Description de la chambre");
        cbStatutChambre = new ComboBox<>();
        cbStatutChambre.getItems().addAll("Disponible", "Occupée", "Maintenance", "Réservée", "En nettoyage");
        cbStatutChambre.setPromptText("Statut");
        cbStatutChambre.setPrefWidth(250);
        int row = 0;
        form.add(createLabel("📋 Numéro:"), 0, row); form.add(tfNum, 1, row++);
        form.add(createLabel("🏷️ Type:"), 0, row); form.add(cbTypeChambre, 1, row++);
        form.add(createLabel("👥 Capacité:"), 0, row); form.add(tfCapacite, 1, row++);
        form.add(createLabel("💰 Prix/jour:"), 0, row); form.add(tfPrix, 1, row++);
        form.add(createLabel("⚙️ Équipements:"), 0, row); form.add(tfEquipements, 1, row++);
        form.add(createLabel("📝 Description:"), 0, row); form.add(tfDescription, 1, row++);
        form.add(createLabel("🔔 Statut:"), 0, row); form.add(cbStatutChambre, 1, row++);
        HBox buttons = new HBox(12);
        buttons.setAlignment(Pos.CENTER);
        buttons.setPadding(new Insets(15, 0, 0, 0));
        Button btnAdd = createStyledButton("➕ Ajouter", "#11998e", "#38ef7d");
        btnAdd.setOnAction(e -> ajouterChambre());
        Button btnModif = createStyledButton("Modifier", "#667eea", "#764ba2");
        btnModif.setOnAction(e -> modifierChambre());
        Button btnSuppr = createStyledButton("Supprimer", "#ff6b6b", "#ee5a52");
        btnSuppr.setOnAction(e -> supprimerChambre());
        Button btnRefresh = createStyledButton("🔄 Actualiser", "#4facfe", "#00f2fe");
        btnRefresh.setOnAction(e -> {
            chargerChambres();
            actualiserCarteCampus();
        });
        buttons.getChildren().addAll(btnAdd, btnModif, btnSuppr, btnRefresh);
        // Table Chambres
        setupTableChambres();
        tableChambres.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                Chambre selected = tableChambres.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    tfNum.setText(selected.numero);
                    cbTypeChambre.setValue(selected.type_chambre);
                    tfCapacite.setText(String.valueOf(selected.capacite));
                    tfPrix.setText(String.valueOf(selected.prix_par_jour));
                    tfEquipements.setText(selected.equipements);
                    tfDescription.setText(selected.description);
                    cbStatutChambre.setValue(selected.statut);
                    // Basculer vers la carte et mettre en évidence la chambre
                    tabPane.getSelectionModel().select(mapTab);
                    ajouterNotification("📍 Chambre sélectionnée depuis le tableau: " + selected.numero);
                }
            }
        });
        vbox.getChildren().addAll(titleLabel, form, buttons, tableChambres);
        VBox.setVgrow(tableChambres, Priority.ALWAYS);
        return vbox;
    }

    private void setupTableChambres() {
        tableChambres.setPlaceholder(new Label("Aucune chambre disponible"));
        tableChambres.getColumns().clear();
        // ID - CORRECTION: SimpleIntegerProperty au lieu de SimpleLongProperty
        TableColumn<Chambre, Integer> colId = new TableColumn<>("ID");
        colId.setCellValueFactory(cellData -> new SimpleIntegerProperty((int) cellData.getValue().chambre_id).asObject());
        colId.setPrefWidth(60);
        colId.setStyle("-fx-alignment: CENTER; -fx-text-fill: #2c3e50;");
        // Numéro
        TableColumn<Chambre, String> colNum = new TableColumn<>("Numéro");
        colNum.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().numero));
        colNum.setPrefWidth(100);
        colNum.setStyle("-fx-alignment: CENTER; -fx-text-fill: #2c3e50;");
        // Type
        TableColumn<Chambre, String> colType = new TableColumn<>("Type");
        colType.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().type_chambre));
        colType.setPrefWidth(120);
        colType.setStyle("-fx-alignment: CENTER; -fx-text-fill: #2c3e50;");
        // Capacité - CORRECTION: SimpleIntegerProperty
        TableColumn<Chambre, Integer> colCapacite = new TableColumn<>("Capacité");
        colCapacite.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().capacite).asObject());
        colCapacite.setPrefWidth(90);
        colCapacite.setStyle("-fx-alignment: CENTER; -fx-text-fill: #2c3e50;");
        // Prix
        TableColumn<Chambre, Double> colPrix = new TableColumn<>("Prix/jour");
        colPrix.setCellValueFactory(cellData -> new SimpleDoubleProperty(cellData.getValue().prix_par_jour).asObject());
        colPrix.setPrefWidth(110);
        colPrix.setStyle("-fx-alignment: CENTER; -fx-text-fill: #2c3e50;");
        colPrix.setCellFactory(column -> new TableCell<Chambre, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(String.format("%.0f FCFA", item));
                    setStyle("-fx-text-fill: #2c3e50; -fx-alignment: CENTER;");
                }
            }
        });
        // Statut - CORRECTION SIMPLIFIÉE
        TableColumn<Chambre, String> colStatut = new TableColumn<>("Statut");
        colStatut.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().statut));
        colStatut.setPrefWidth(120);
        colStatut.setCellFactory(column -> new TableCell<Chambre, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("-fx-alignment: CENTER;");
                    // Appliquer le style selon le statut
                    switch (item) {
                        case "Disponible":
                            setStyle("-fx-background-color: #d4edda; -fx-text-fill: #155724; -fx-font-weight: bold; -fx-alignment: CENTER;");
                            break;
                        case "Occupée":
                            setStyle("-fx-background-color: #f8d7da; -fx-text-fill: #721c24; -fx-font-weight: bold; -fx-alignment: CENTER;");
                            break;
                        case "Maintenance":
                            setStyle("-fx-background-color: #fff3cd; -fx-text-fill: #856404; -fx-font-weight: bold; -fx-alignment: CENTER;");
                            break;
                        case "Réservée":
                            setStyle("-fx-background-color: #cce5ff; -fx-text-fill: #004085; -fx-font-weight: bold; -fx-alignment: CENTER;");
                            break;
                        default:
                            setStyle("-fx-alignment: CENTER; -fx-text-fill: #2c3e50;");
                    }
                }
            }
        });
        // Équipements
        TableColumn<Chambre, String> colEquip = new TableColumn<>("Équipements");
        colEquip.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().equipements));
        colEquip.setPrefWidth(200);
        colEquip.setStyle("-fx-alignment: CENTER_LEFT; -fx-text-fill: #2c3e50;");
        // Description
        TableColumn<Chambre, String> colDesc = new TableColumn<>("Description");
        colDesc.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().description));
        colDesc.setPrefWidth(250);
        colDesc.setStyle("-fx-alignment: CENTER_LEFT; -fx-text-fill: #2c3e50;");
        tableChambres.getColumns().addAll(colId, colNum, colType, colCapacite, colPrix, colStatut, colEquip, colDesc);
        tableChambres.setItems(chambresList);
        tableChambres.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    private void ajouterChambre() {
        try {
            if (cbTypeChambre.getValue() == null) {
                showWarning("Veuillez sélectionner un type de chambre");
                return;
            }
            if (tfNum.getText().isEmpty()) {
                showWarning("Veuillez saisir un numéro de chambre");
                return;
            }
            // Récupérer le statut choisi (par défaut "Disponible" si non sélectionné)
            String statut = cbStatutChambre.getValue() != null ? cbStatutChambre.getValue() : "Disponible";
            long id = gestion.ajouterChambre(
                    tfNum.getText(),
                    cbTypeChambre.getValue(),
                    Integer.parseInt(tfCapacite.getText()),
                    Double.parseDouble(tfPrix.getText()),
                    tfEquipements.getText(),
                    tfDescription.getText(),
                    statut // Ajout du statut
            );
            showSuccess("✓ Chambre ajoutée avec succès ! ID = " + id + " (Statut: " + statut + ")");
            ajouterNotification("Nouvelle chambre ajoutée: " + tfNum.getText() + " (ID: " + id + ", Statut: " + statut + ")");
            envoyerNotificationTCP("NOUVELLE_CHAMBRE:" + tfNum.getText() + ":" + cbTypeChambre.getValue() + ":" + id + ":" + statut);
            chargerChambres();
            actualiserCarteCampus();
            viderChampsChambre();
            // Basculer vers la carte pour voir la nouvelle chambre
            tabPane.getSelectionModel().select(mapTab);
        } catch (NumberFormatException ex) {
            showError("Erreur : Veuillez entrer des valeurs numériques valides pour la capacité et le prix");
        } catch (Exception ex) {
            showError("Erreur : " + ex.getMessage());
        }
    }

    private void modifierChambre() {
        Chambre selected = tableChambres.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Veuillez sélectionner une chambre à modifier");
            return;
        }
        try {
            System.out.println("=== DEBUG MODIFICATION CHAMBRE ===");
            System.out.println("Chambre sélectionnée - ID: " + selected.chambre_id + ", Numéro: " + selected.numero);
            System.out.println("Nouveau statut sélectionné: " + cbStatutChambre.getValue());
            System.out.println("Champs modifiés:");
            System.out.println(" - Numéro: " + tfNum.getText());
            System.out.println(" - Type: " + cbTypeChambre.getValue());
            System.out.println(" - Capacité: " + tfCapacite.getText());
            System.out.println(" - Prix: " + tfPrix.getText());
            System.out.println(" - Statut: " + cbStatutChambre.getValue());
            // Appel CORBA pour modifier la chambre
            System.out.println("Appel de gestion.modifierChambre()...");
            gestion.modifierChambre((int) selected.chambre_id,
                    tfNum.getText(),
                    cbTypeChambre.getValue(),
                    Integer.parseInt(tfCapacite.getText()),
                    Double.parseDouble(tfPrix.getText()),
                    tfEquipements.getText(),
                    tfDescription.getText()
            );
            if (cbStatutChambre.getValue() != null) {
                System.out.println("Appel de gestion.majStatutChambre() avec statut: " + cbStatutChambre.getValue());
                gestion.majStatutChambre(selected.chambre_id, cbStatutChambre.getValue());
            }
            showSuccess("✓ Chambre modifiée avec succès !");
            ajouterNotification("Chambre modifiée: " + tfNum.getText() + " (ID: " + selected.chambre_id + ")");
            envoyerNotificationTCP("CHAMBRE_MODIFIEE:" + tfNum.getText() + ":" + selected.chambre_id);
            // Rechargement
            System.out.println("Rechargement des chambres...");
            chargerChambres();
            System.out.println("Actualisation de la carte...");
            actualiserCarteCampus();
            viderChampsChambre();
            System.out.println("=== FIN DEBUG ===");
        } catch (NumberFormatException ex) {
            showError("Erreur : Veuillez entrer des valeurs numériques valides pour la capacité et le prix");
        } catch (Exception ex) {
            showError("Erreur lors de la modification: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void supprimerChambre() {
        Chambre selected = tableChambres.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Veuillez sélectionner une chambre à supprimer");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Suppression de chambre");
        confirm.setHeaderText("Supprimer la chambre " + selected.numero + " ?");
        confirm.setContentText("Êtes-vous sûr de vouloir supprimer cette chambre ?\n\n"
                + "Attention : si des réservations existent, la suppression sera refusée.");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    gestion.supprimerChambre(selected.chambre_id);
                    // Recharger pour vérifier si supprimé
                    chargerChambres();
                    // Vérifier si encore présent
                    boolean stillExists = chambresList.stream().anyMatch(c -> c.chambre_id == selected.chambre_id);
                    if (stillExists) {
                        throw new Exception("La chambre n'a pas été supprimée (peut-être à cause de réservations actives).");
                    }
                    showSuccess("✓ Chambre supprimée avec succès !");
                    ajouterNotification("Chambre supprimée : " + selected.numero);
                    envoyerNotificationTCP("CHAMBRE_SUPPRIMEE:" + selected.numero + ":" + selected.chambre_id);
                    actualiserCarteCampus();
                } catch (Exception ex) {
                    // Message personnalisé et clair
                    String msg = ex.getMessage();
                    if (msg.contains("réservation")) {
                        showError("Suppression impossible :\n\n" + msg);
                    } else {
                        showError("Erreur lors de la suppression :\n\n" + msg);
                    }
                }
            }
        });
    }

    private void chargerChambres() {
        try {
            chambresList.clear();
            Chambre[] chambres = gestion.listerToutesChambres();
            if (chambres != null && chambres.length > 0) {
                chambresList.addAll(chambres);
                System.out.println("✓ " + chambres.length + " chambres chargées");
            } else {
                System.out.println("Aucune chambre trouvée");
            }
        } catch (Exception e) {
            System.err.println("Erreur lors du chargement des chambres: " + e.getMessage());
            showWarning("Impossible de charger les chambres. Vérifiez la connexion au serveur.");
            e.printStackTrace();
        }
    }

    private void viderChampsChambre() {
        tfNum.clear();
        cbTypeChambre.setValue(null);
        tfCapacite.clear();
        tfPrix.clear();
        tfEquipements.clear();
        tfDescription.clear();
        cbStatutChambre.setValue(null);
    }

    // ===================== PANEL CLIENTS =====================
    private VBox panelClients() {
        VBox vbox = new VBox(15);
        vbox.setPadding(new Insets(20));
        vbox.setStyle("-fx-background-color: rgba(255,255,255,0.95); -fx-background-radius: 20;");
        Label titleLabel = new Label("👤 Gestion des Clients");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 24));
        titleLabel.setTextFill(Color.web("#667eea"));
        GridPane form = new GridPane();
        form.setHgap(15);
        form.setVgap(12);
        form.setPadding(new Insets(15));
        form.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 15; -fx-padding: 20;");
        tfNom = createStyledTextField("ex: Mariam Leffi");
        tfEmail = createStyledTextField("ex: mariam@gmail.com");
        tfTel = createStyledTextField("ex: +216 27 860 277");
        tfOrganisation = createStyledTextField("ex: Telnet");
        cbTypeClient = new ComboBox<>();
        cbTypeClient.getItems().addAll("Étudiant", "Professeur", "Visiteur", "Personnel", "Autre");
        cbTypeClient.setPromptText("Type de client");
        cbTypeClient.setPrefWidth(250);
        int row = 0;
        form.add(createLabel("👤 Nom:"), 0, row); form.add(tfNom, 1, row++);
        form.add(createLabel("📧 Email:"), 0, row); form.add(tfEmail, 1, row++);
        form.add(createLabel("📞 Téléphone:"), 0, row); form.add(tfTel, 1, row++);
        form.add(createLabel("🏷️ Type:"), 0, row); form.add(cbTypeClient, 1, row++);
        form.add(createLabel("🏢 Organisation:"), 0, row); form.add(tfOrganisation, 1, row++);
        HBox buttons = new HBox(12);
        buttons.setAlignment(Pos.CENTER);
        buttons.setPadding(new Insets(15, 0, 0, 0));
        Button btnAdd = createStyledButton("➕ Ajouter", "#11998e", "#38ef7d");
        btnAdd.setOnAction(e -> creerClient());
        Button btnModif = createStyledButton("Modifier", "#667eea", "#764ba2");
        btnModif.setOnAction(e -> modifierClient());
        Button btnSuppr = createStyledButton("Supprimer", "#ff6b6b", "#ee5a52");
        btnSuppr.setOnAction(e -> supprimerClient());
        Button btnRefresh = createStyledButton("🔄 Actualiser", "#4facfe", "#00f2fe");
        btnRefresh.setOnAction(e -> chargerClients());
        buttons.getChildren().addAll(btnAdd, btnModif, btnSuppr, btnRefresh);
        setupTableClients();
        tableClients.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                Client selected = tableClients.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    tfNom.setText(selected.nom);
                    tfEmail.setText(selected.email);
                    tfTel.setText(selected.telephone);
                    cbTypeClient.setValue(selected.type_client);
                    tfOrganisation.setText(selected.organisation);
                }
            }
        });
        vbox.getChildren().addAll(titleLabel, form, buttons, tableClients);
        VBox.setVgrow(tableClients, Priority.ALWAYS);
        return vbox;
    }

    private void setupTableClients() {
        tableClients.getColumns().clear();
        TableColumn<Client, Integer> colId = new TableColumn<>("ID");
        colId.setCellValueFactory(data -> new SimpleIntegerProperty((int) data.getValue().client_id).asObject());
        colId.setPrefWidth(60);
        colId.setStyle("-fx-alignment: CENTER;");
        TableColumn<Client, String> colNom = new TableColumn<>("Nom");
        colNom.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().nom));
        colNom.setPrefWidth(200);
        TableColumn<Client, String> colEmail = new TableColumn<>("Email");
        colEmail.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().email));
        colEmail.setPrefWidth(220);
        TableColumn<Client, String> colTel = new TableColumn<>("Téléphone");
        colTel.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().telephone));
        colTel.setPrefWidth(150);
        colTel.setStyle("-fx-alignment: CENTER;");
        TableColumn<Client, String> colType = new TableColumn<>("Type");
        colType.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().type_client));
        colType.setPrefWidth(120);
        colType.setStyle("-fx-alignment: CENTER;");
        TableColumn<Client, String> colOrg = new TableColumn<>("Organisation");
        colOrg.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().organisation));
        colOrg.setPrefWidth(200);
        tableClients.getColumns().addAll(colId, colNom, colEmail, colTel, colType, colOrg);
        tableClients.setItems(clientsList);
        tableClients.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    private void creerClient() {
        try {
            if (cbTypeClient.getValue() == null) {
                showWarning("Veuillez sélectionner un type de client");
                return;
            }
            if (tfNom.getText().isEmpty()) {
                showWarning("Veuillez saisir un nom");
                return;
            }
            long id = gestion.creerClient(
                    tfNom.getText(),
                    tfEmail.getText(),
                    tfTel.getText(),
                    cbTypeClient.getValue(),
                    tfOrganisation.getText()
            );
            showSuccess("✓ Client créé avec succès ! ID = " + id);
            ajouterNotification("Nouveau client créé: " + tfNom.getText() + " (ID: " + id + ")");
            envoyerNotificationTCP("NOUVEAU_CLIENT:" + tfNom.getText() + ":" + cbTypeClient.getValue() + ":" + id);
            chargerClients();
            chargerListesReservations(); // Mettre à jour les listes déroulantes
            viderChampsClient();
        } catch (Exception ex) {
            showError("Erreur : " + ex.getMessage());
        }
    }

    private void modifierClient() {
        Client selected = tableClients.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Veuillez sélectionner un client à modifier");
            return;
        }
        try {
            gestion.modifierClient((int) selected.client_id,
                    tfNom.getText(),
                    tfEmail.getText(),
                    tfTel.getText(),
                    cbTypeClient.getValue(),
                    tfOrganisation.getText()
            );
            showSuccess("✓ Client modifié avec succès !");
            ajouterNotification("Client modifié: " + tfNom.getText() + " (ID: " + selected.client_id + ")");
            envoyerNotificationTCP("CLIENT_MODIFIE:" + tfNom.getText() + ":" + selected.client_id);
            chargerClients();
            viderChampsClient();
        } catch (Exception ex) {
            showError("Erreur lors de la modification: " + ex.getMessage());
        }
    }

    private void supprimerClient() {
        Client selected = tableClients.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Veuillez sélectionner un client à supprimer");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmation de suppression");
        confirm.setHeaderText("Supprimer le client ?");
        confirm.setContentText("ATTENTION : Toutes les réservations associées à ce client seront automatiquement annulées.\n\n" +
                "Voulez-vous vraiment supprimer " + selected.nom + " (ID: " + selected.client_id + ") ?");
        confirm.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                try {
                    gestion.supprimerClient((int) selected.client_id);
                    // Recharger pour vérifier
                    chargerClients();
                    boolean stillExists = clientsList.stream().anyMatch(c -> c.client_id == selected.client_id);
                    if (stillExists) {
                        throw new Exception("Le client n'a pas été supprimé (vérifiez les réservations ou le serveur).");
                    }
                    showSuccess("✓ Client supprimé avec succès !\n" +
                            "Toutes les réservations associées ont été automatiquement annulées.");
                    ajouterNotification("Client supprimé: " + selected.nom + " (ID: " + selected.client_id + ")");
                    envoyerNotificationTCP("CLIENT_SUPPRIME:" + selected.nom + ":" + selected.client_id);
                    chargerListesReservations(); // Mettre à jour les listes déroulantes
                    chargerToutesReservations(); // Recharger les réservations
                } catch (Exception e) {
                    showError("Erreur lors de la suppression : \n\n" +
                            e.getMessage() + "\n\n" +
                            "Veuillez vérifier que le client existe toujours.");
                }
            }
        });
    }

    private void chargerClients() {
        try {
            clientsList.clear();
            Client[] clients = gestion.listerTousClients();
            if (clients != null && clients.length > 0) {
                clientsList.addAll(clients);
                System.out.println("✓ " + clients.length + " clients chargés");
            } else {
                System.out.println("Aucun client trouvé");
            }
        } catch (Exception e) {
            System.err.println("Erreur lors du chargement des clients: " + e.getMessage());
            showWarning("Impossible de charger les clients. Vérifiez la connexion au serveur.");
            e.printStackTrace();
        }
    }

    private void viderChampsClient() {
        tfNom.clear();
        tfEmail.clear();
        tfTel.clear();
        cbTypeClient.setValue(null);
        tfOrganisation.clear();
    }

    // ===================== PANEL RÉSERVATIONS =====================
    private VBox panelReservations() {
        VBox vbox = new VBox(15);
        vbox.setPadding(new Insets(20));
        vbox.setStyle("-fx-background-color: rgba(255,255,255,0.97); -fx-background-radius: 20;");

        Label titleLabel = new Label("Gestion des Réservations");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 26));
        titleLabel.setTextFill(Color.web("#667eea"));

        GridPane form = new GridPane();
        form.setHgap(15); form.setVgap(12);
        form.setPadding(new Insets(20));
        form.setStyle("-fx-background: #f8f9fa; -fx-background-radius: 15;");

        cbClientResa = new ComboBox<>(); cbClientResa.setPromptText("Sélectionner un client");
        cbChambreResa = new ComboBox<>(); cbChambreResa.setPromptText("Sélectionner une chambre disponible");
        dpDateDebut = new DatePicker(); dpDateDebut.setPromptText("Date de début");
        dpDateFin = new DatePicker(); dpDateFin.setPromptText("Date de fin");

        int row = 0;
        form.add(createLabel("Client:"), 0, row); form.add(cbClientResa, 1, row++);
        form.add(createLabel("Chambre:"), 0, row); form.add(cbChambreResa, 1, row++);
        form.add(createLabel("Du:"), 0, row); form.add(dpDateDebut, 1, row++);
        form.add(createLabel("Au:"), 0, row); form.add(dpDateFin, 1, row++);

        HBox buttons = new HBox(15);
        buttons.setAlignment(Pos.CENTER);
        buttons.setPadding(new Insets(20, 0, 0, 0));

        Button btnCreer = createStyledButton("Créer Réservation", "#11998e", "#38ef7d");
        Button btnConfirmer = createStyledButton("Confirmer", "#667eea", "#764ba2");
        Button btnAnnuler = createStyledButton("Annuler", "#ff6b6b", "#ee5a52");
        Button btnTerminer = createStyledButton("Terminer", "#17a2b8", "#138496");
        Button btnRefresh = createStyledButton("Actualiser", "#4facfe", "#00f2fe");

        btnCreer.setOnAction(e -> creerReservation());
        btnConfirmer.setOnAction(e -> changerStatutReservation("Confirmée"));
        btnAnnuler.setOnAction(e -> changerStatutReservation("Annulée"));
        btnTerminer.setOnAction(e -> changerStatutReservation("Terminée"));
        btnRefresh.setOnAction(e -> {
            chargerListesReservations();
            chargerToutesReservations();
            chargerChambres();
            actualiserCarteCampus();
        });

        buttons.getChildren().addAll(btnCreer, btnConfirmer, btnAnnuler, btnTerminer, btnRefresh);

        setupTableReservations();
        chargerListesReservations();

        vbox.getChildren().addAll(titleLabel, form, buttons, tableReservations);
        VBox.setVgrow(tableReservations, Priority.ALWAYS);
        return vbox;
    }

    // ===================== TABLEAU RÉSERVATIONS (plus lisible) =====================
    private void setupTableReservations() {
        tableReservations.getColumns().clear();

        TableColumn<Reservation, Integer> colId = new TableColumn<>("ID");
        colId.setCellValueFactory(d -> new SimpleIntegerProperty((int) d.getValue().reservation_id).asObject());
        colId.setPrefWidth(60);

        TableColumn<Reservation, String> colClient = new TableColumn<>("Client");
        colClient.setCellValueFactory(d -> {
            Client c = clientsList.stream()
                    .filter(cl -> cl.client_id == d.getValue().client_id)
                    .findFirst().orElse(null);
            return new SimpleStringProperty(c != null ? c.nom : "Inconnu");
        });
        colClient.setPrefWidth(180);

        TableColumn<Reservation, String> colChambre = new TableColumn<>("Chambre");
        colChambre.setCellValueFactory(d -> {
            Chambre ch = chambresList.stream()
                    .filter(c -> c.chambre_id == d.getValue().chambre_id)
                    .findFirst().orElse(null);
            return new SimpleStringProperty(ch != null ? ch.numero + " (" + ch.type_chambre + ")" : "Inconnue");
        });
        colChambre.setPrefWidth(160);

        TableColumn<Reservation, String> colPeriode = new TableColumn<>("Période");
        colPeriode.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().date_debut + " → " + d.getValue().date_fin));
        colPeriode.setPrefWidth(200);

        TableColumn<Reservation, Double> colTotal = new TableColumn<>("Total");
        colTotal.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().total).asObject());
        colTotal.setPrefWidth(110);
        colTotal.setCellFactory(tc -> new TableCell<Reservation, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("%.0f FCFA", item));
            }
        });

        TableColumn<Reservation, String> colStatut = new TableColumn<>("Statut");
        colStatut.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().statut));
        colStatut.setPrefWidth(130);
        colStatut.setCellFactory(tc -> new TableCell<Reservation, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null); setStyle("");
                } else {
                    setText(item);
                    switch (item) {
                        case "En attente":
                            setStyle("-fx-background-color: #fff3cd; -fx-text-fill: #856404; -fx-font-weight: bold;");
                            break;
                        case "Confirmée":
                            setStyle("-fx-background-color: #d4edda; -fx-text-fill: #155724; -fx-font-weight: bold;");
                            break;
                        case "Annulée":
                            setStyle("-fx-background-color: #f8d7da; -fx-text-fill: #721c24; -fx-font-weight: bold;");
                            break;
                        case "Terminée":
                            setStyle("-fx-background-color: #d1ecf1; -fx-text-fill: #0c5460; -fx-font-weight: bold;");
                            break;
                        default:
                            setStyle("-fx-font-weight: bold;");
                            break;
                    }
                }
            }
        });

        tableReservations.getColumns().addAll(colId, colClient, colChambre, colPeriode, colTotal, colStatut);
        tableReservations.setItems(reservationsList);
        tableReservations.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    // ===================== LISTES DÉROULANTES =====================
    private void chargerListesReservations() {
        try {
            cbClientResa.getItems().clear();
            for (Client c : gestion.listerTousClients())
                cbClientResa.getItems().add(c.client_id + " - " + c.nom + " (" + c.email + ")");

            cbChambreResa.getItems().clear();
            for (Chambre ch : gestion.listerToutesChambres()) {
                String status = "Disponible".equals(ch.statut) ? "Disponible" : ch.statut;
                cbChambreResa.getItems().add(ch.chambre_id + " - " + ch.numero + " [" + ch.type_chambre + "] " + status);
            }
        } catch (Exception e) {
            showError("Erreur chargement listes : " + e.getMessage());
        }
    }

    // ===================== CRÉATION RÉSERVATION (En attente) =====================
    private void creerReservation() {
        try {
            if (cbClientResa.getValue() == null || cbChambreResa.getValue() == null ||
                    dpDateDebut.getValue() == null || dpDateFin.getValue() == null) {
                showWarning("Tous les champs sont obligatoires");
                return;
            }
            if (dpDateFin.getValue().isBefore(dpDateDebut.getValue())) {
                showWarning("La date de fin doit être après la date de début");
                return;
            }

            int clientId = Integer.parseInt(cbClientResa.getValue().split(" - ")[0]);
            int chambreId = Integer.parseInt(cbChambreResa.getValue().split(" - ")[0]);
            String debut = dpDateDebut.getValue().toString();
            String fin = dpDateFin.getValue().toString();

            long id = gestion.creerReservation(clientId, chambreId, debut, fin);

            showSuccess("Réservation créée !\nID: " + id + "\nStatut: En attente");
            ajouterNotification("Nouvelle réservation ID " + id);
            envoyerNotificationTCP("NOUVELLE_RESERVATION:" + id);

            chargerChambres();
            chargerToutesReservations();
            actualiserCarteCampus();
            viderChampsReservation();

        } catch (Exception ex) {
            showError("Erreur création : " + ex.getMessage());
        }
    }

    // ===================== CHANGEMENT DE STATUT UNIVERSEL =====================
    private void changerStatutReservation(String nouveauStatut) {
        Reservation selected = tableReservations.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Veuillez sélectionner une réservation");
            return;
        }
        if (selected.statut.equals(nouveauStatut)) {
            showWarning("La réservation est déjà " + nouveauStatut);
            return;
        }

        String action;
        if ("Confirmée".equals(nouveauStatut)) {
            action = "confirmer";
        } else if ("Annulée".equals(nouveauStatut)) {
            action = "annuler";
        } else if ("Terminée".equals(nouveauStatut)) {
            action = "terminer";
        } else {
            action = "modifier";
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmation");
        confirm.setHeaderText(action.substring(0, 1).toUpperCase() + action.substring(1) + " la réservation n°" + selected.reservation_id + " ?");

        confirm.showAndWait().ifPresent(resp -> {
            if (resp == ButtonType.OK) {
                try {
                    boolean ok = gestion.majStatutReservation((int) selected.reservation_id, nouveauStatut);
                    if (ok) {
                        showSuccess("Réservation passée à : " + nouveauStatut);
                        ajouterNotification("Réservation " + selected.reservation_id + " → " + nouveauStatut);
                        envoyerNotificationTCP("RESERVATION_" + nouveauStatut.toUpperCase() + ":" + selected.reservation_id);

                        chargerChambres();
                        chargerToutesReservations();
                        actualiserCarteCampus();
                    } else {
                        showError("Échec de la mise à jour");
                    }
                } catch (Exception ex) {
                    showError("Erreur : " + ex.getMessage());
                }
            }
        });
    }

    // ===================== CHARGEMENT TOUTES LES RÉSERVATIONS =====================
    private void chargerToutesReservations() {
        reservationsList.clear();
        try {
            for (Chambre c : gestion.listerToutesChambres()) {
                Reservation[] resas = gestion.reservationsChambre((int) c.chambre_id);
                if (resas != null) reservationsList.addAll(resas);
            }
        } catch (Exception e) {
            showError("Erreur chargement réservations : " + e.getMessage());
        }
    }

    private void viderChampsReservation() {
        cbClientResa.setValue(null);
        cbChambreResa.setValue(null);
        dpDateDebut.setValue(null);
        dpDateFin.setValue(null);
    }

    // ===================== PANEL NOTIFICATIONS =====================
    private VBox panelNotifications() {
        VBox vbox = new VBox(15);
        vbox.setPadding(new Insets(20));
        vbox.setStyle("-fx-background-color: rgba(255,255,255,0.95); -fx-background-radius: 20;");
        Label titleLabel = new Label("🔔 Journal des Activités (TCP)");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 24));
        titleLabel.setTextFill(Color.web("#667eea"));
        // Zone de texte pour les notifications
        taNotifications.setPrefHeight(500);
        taNotifications.setEditable(false);
        taNotifications.setWrapText(true);
        taNotifications.setStyle("-fx-control-inner-background: #f8f9fa; -fx-font-family: 'Consolas'; -fx-font-size: 13px;");
        // Liste pour afficher les notifications
        lvNotifications.setItems(notificationsList);
        lvNotifications.setPrefHeight(500);
        lvNotifications.setStyle("-fx-background-color: #f8f9fa; -fx-font-family: 'Consolas'; -fx-font-size: 13px;");
        TabPane notificationTabs = new TabPane();
        Tab tabText = new Tab("📝 Journal", taNotifications);
        tabText.setClosable(false);
        Tab tabList = new Tab("📋 Liste", lvNotifications);
        tabList.setClosable(false);
        notificationTabs.getTabs().addAll(tabText, tabList);
        notificationTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        HBox buttons = new HBox(12);
        buttons.setAlignment(Pos.CENTER);
        buttons.setPadding(new Insets(15, 0, 0, 0));
        Button btnClear = createStyledButton("🗑️ Effacer", "#ff6b6b", "#ee5a52");
        btnClear.setOnAction(e -> {
            taNotifications.clear();
            notificationsList.clear();
            notificationCount = 0;
            if (notificationsTab != null) {
                notificationsTab.setText("🔔 Notifications");
            }
        });
        Button btnTestTCP = createStyledButton("📡 Test TCP", "#4facfe", "#00f2fe");
        btnTestTCP.setOnAction(e -> {
            envoyerNotificationTCP("TEST:Notification de test depuis le client");
            ajouterNotification("Test TCP envoyé");
        });
        Button btnStats = createStyledButton("📊 Statistiques", "#11998e", "#38ef7d");
        btnStats.setOnAction(e -> {
            String stats = "=== STATISTIQUES ===\n" +
                    "Total notifications: " + notificationCount + "\n" +
                    "Chambres chargées: " + chambresList.size() + "\n" +
                    "Clients chargés: " + clientsList.size() + "\n" +
                    "Réservations chargées: " + reservationsList.size() + "\n" +
                    "=========================";
            ajouterNotification(stats);
        });
        buttons.getChildren().addAll(btnClear, btnTestTCP, btnStats);
        vbox.getChildren().addAll(titleLabel, notificationTabs, buttons);
        VBox.setVgrow(notificationTabs, Priority.ALWAYS);
        return vbox;
    }

    private void ajouterNotification(String message) {
        String timestamp = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        String notification = String.format("[%s] %s", timestamp, message);
        // Ajouter à la zone de texte
        taNotifications.appendText(notification + "\n");
        // Ajouter à la liste
        notificationsList.add(0, notification); // Ajouter au début
        // Faire défiler vers le bas
        taNotifications.setScrollTop(Double.MAX_VALUE);
        notificationCount++;
        // Mettre à jour le titre de l'onglet avec un badge
        if (notificationsTab != null) {
            notificationsTab.setText("🔔 Notifications (" + notificationCount + ")");
        }
    }

    // ===================== MÉTHODES UTILITAIRES =====================
    private TextField createStyledTextField(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.setPrefWidth(300);
        tf.setStyle("-fx-background-radius: 10; -fx-padding: 10; -fx-font-size: 14px;");
        return tf;
    }

    private Label createLabel(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 14));
        label.setTextFill(Color.web("#2c3e50"));
        return label;
    }

    private Button createStyledButton(String text, String color1, String color2) {
        Button btn = new Button(text);
        btn.setPrefWidth(180);
        btn.setStyle(String.format(
                "-fx-background-color: linear-gradient(to bottom, %s, %s); " +
                        "-fx-text-fill: white; " +
                        "-fx-background-radius: 25; " +
                        "-fx-padding: 12 25; " +
                        "-fx-font-weight: bold; " +
                        "-fx-font-size: 14px; " +
                        "-fx-cursor: hand;",
                color1, color2
        ));
        btn.setEffect(new DropShadow(10, Color.rgb(0, 0, 0, 0.3)));
        // Animation hover
        btn.setOnMouseEntered(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(200), btn);
            st.setToX(1.05);
            st.setToY(1.05);
            st.play();
        });
        btn.setOnMouseExited(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(200), btn);
            st.setToX(1.0);
            st.setToY(1.0);
            st.play();
        });
        return btn;
    }

    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Succès");
        alert.setHeaderText("Opération réussie");
        alert.setContentText(message);
        // Personnaliser l'icône
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setStyle("-fx-background-color: white; -fx-border-color: #d4edda; -fx-border-width: 2;");
        alert.showAndWait();
        // Animation
        FadeTransition fade = new FadeTransition(Duration.millis(300), dialogPane);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erreur");
        alert.setHeaderText("Une erreur est survenue");
        alert.setContentText(message);
        // Personnaliser l'icône
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setStyle("-fx-background-color: white; -fx-border-color: #f8d7da; -fx-border-width: 2;");
        alert.showAndWait();
    }

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Attention");
        alert.setHeaderText("Veuillez vérifier");
        alert.setContentText(message);
        // Personnaliser l'icône
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setStyle("-fx-background-color: white; -fx-border-color: #fff3cd; -fx-border-width: 2;");
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}