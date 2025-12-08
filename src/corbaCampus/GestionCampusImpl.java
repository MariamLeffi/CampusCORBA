package corbaCampus;

import Campus.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class GestionCampusImpl extends GestionCampusPOA {

    private final Connection conn;

    public GestionCampusImpl() {
        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            String url = "jdbc:sqlserver://DESKTOP-B954DLK\\SQLEXPRESS;databaseName=Campus;integratedSecurity=true;encrypt=false;";
            conn = DriverManager.getConnection(url);
            conn.setAutoCommit(true);
            System.out.println("Connexion réussie à la base Campus !");
        } catch (Exception e) {
            System.err.println("ERREUR DE CONNEXION À LA BASE DE DONNÉES");
            e.printStackTrace();
            throw new RuntimeException("Impossible de se connecter à SQL Server", e);
        }
    }

    /** Notification TCP vers tous les clients connectés */
    private void envoyerNotification(String message) {
        try {
            ServeurCampus.broadcastNotification("[Serveur] " + message);
            System.out.println("Notification envoyée: " + message);
        } catch (Exception e) {
            System.err.println("Erreur envoi TCP: " + e.getMessage());
        }
    }

    // ==================== CHAMBRES ====================

    @Override
    public int ajouterChambre(String numero, String type_chambre, int capacite,
                              double prix_par_jour, String equipements, String description, String statut) {
        String sql = "INSERT INTO chambre (numero, type_chambre, capacite, prix_par_jour, equipements, description, statut) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, numero);
            ps.setString(2, type_chambre);
            ps.setInt(3, capacite);
            ps.setDouble(4, prix_par_jour);
            ps.setString(5, equipements);
            ps.setString(6, description);
            ps.setString(7, statut);

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    System.out.println("Nouvelle chambre ajoutée: " + numero + " (ID: " + id + ") Statut: " + statut);
                    envoyerNotification("NOUVELLE_CHAMBRE:" + numero + ":" + type_chambre + ":" + id + ":" + statut);
                    return id;
                }
            }
        } catch (SQLException e) {
            System.err.println("Erreur ajout chambre: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Erreur lors de l'ajout de la chambre: " + e.getMessage());
        }
        return -1;
    }

    @Override
    public void modifierChambre(int id, String numero, String type_chambre, int capacite,
                                double prix_par_jour, String equipements, String description) {
        String sql = "UPDATE chambre SET numero=?, type_chambre=?, capacite=?, prix_par_jour=?, "
                + "equipements=?, description=? WHERE chambre_id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, numero);
            ps.setString(2, type_chambre);
            ps.setInt(3, capacite);
            ps.setDouble(4, prix_par_jour);
            ps.setString(5, equipements);
            ps.setString(6, description);
            ps.setInt(7, id);

            int rows = ps.executeUpdate();
            if (rows == 0) throw new RuntimeException("Chambre non trouvée (ID: " + id + ")");
            System.out.println("Chambre modifiée: " + numero + " (ID: " + id + ")");
            envoyerNotification("CHAMBRE_MODIFIEE:" + numero + ":" + id);
        } catch (SQLException e) {
            throw new RuntimeException("Erreur modification chambre: " + e.getMessage());
        }
    }

    @Override
    public void supprimerChambre(int id) {
        String numero = "Inconnue";
        int reservationsImpactees = 0;

        try {
            conn.setAutoCommit(false);

            // 1. Récupérer le numéro de chambre
            try (PreparedStatement ps = conn.prepareStatement("SELECT numero FROM chambre WHERE chambre_id=?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new RuntimeException("Chambre introuvable (ID: " + id + ")");
                    }
                    numero = rs.getString("numero");
                }
            }

            // 2. ANNULER + DÉTACHER toutes les réservations (on met chambre_id à NULL)
            // → Cela casse le lien avec la chambre sans perdre l'historique
            String sqlDetach = " UPDATE reservation SET statut = 'Annulée', chambre_id = NULL WHERE chambre_id = ? ";

            try (PreparedStatement ps = conn.prepareStatement(sqlDetach)) {
                ps.setInt(1, id);
                reservationsImpactees = ps.executeUpdate();
            }

            // 3. Maintenant on peut supprimer la chambre sans conflit
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM chambre WHERE chambre_id=?")) {
                ps.setInt(1, id);
                int deleted = ps.executeUpdate();
                if (deleted == 0) {
                    throw new RuntimeException("Échec suppression de la chambre (ID: " + id + ")");
                }
            }

            conn.commit();

            System.out.println("Chambre supprimée : " + numero + " (ID: " + id + ")");
            if (reservationsImpactees > 0) {
                System.out.println("   → " + reservationsImpactees + " réservation(s) détachée(s) et annulée(s)");
            }

            // Notification à tous les clients
            envoyerNotification("CHAMBRE_SUPPRIMEE:" + numero + ":" + id + ":" + reservationsImpactees);

        } catch (Exception e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw new RuntimeException("Impossible de supprimer la chambre : " + e.getMessage());
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }    @Override
    public Chambre[] listerToutesChambres() {
        List<Chambre> list = new ArrayList<>();
        String sql = "SELECT * FROM chambre ORDER BY chambre_id";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Chambre c = new Chambre();
                c.chambre_id = rs.getInt("chambre_id");
                c.numero = rs.getString("numero");
                c.type_chambre = rs.getString("type_chambre");
                c.capacite = rs.getInt("capacite");
                c.prix_par_jour = rs.getDouble("prix_par_jour");
                c.equipements = rs.getString("equipements");
                c.statut = rs.getString("statut");
                c.description = rs.getString("description");
                list.add(c);
            }
        } catch (SQLException e) {
            System.err.println("Erreur listing chambres: " + e.getMessage());
        }
        return list.toArray(new Chambre[0]);
    }

    @Override
    public Chambre rechercherChambre(int id) {
        String sql = "SELECT * FROM chambre WHERE chambre_id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Chambre c = new Chambre();
                    c.chambre_id = rs.getInt("chambre_id");
                    c.numero = rs.getString("numero");
                    c.type_chambre = rs.getString("type_chambre");
                    c.capacite = rs.getInt("capacite");
                    c.prix_par_jour = rs.getDouble("prix_par_jour");
                    c.equipements = rs.getString("equipements");
                    c.statut = rs.getString("statut");
                    c.description = rs.getString("description");
                    return c;
                }
            }
        } catch (SQLException e) {
            System.err.println("Erreur recherche chambre: " + e.getMessage());
        }
        throw new RuntimeException("Chambre non trouvée (ID: " + id + ")");
    }

    @Override
    public void majStatutChambre(int id, String statut) {
        String sql = "UPDATE chambre SET statut=? WHERE chambre_id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, statut);
            ps.setInt(2, id);
            if (ps.executeUpdate() == 0) throw new RuntimeException("Chambre introuvable");
            System.out.println("Statut chambre " + id + " → " + statut);
            envoyerNotification("STATUT_CHAMBRE:" + id + ":" + statut);
        } catch (SQLException e) {
            throw new RuntimeException("Erreur mise à jour statut chambre: " + e.getMessage());
        }
    }

    // ==================== CLIENTS ====================

    @Override
    public int creerClient(String nom, String email, String telephone, String type_client, String organisation) {
        String sql = "INSERT INTO client (nom, email, telephone, type_client, organisation) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nom);
            ps.setString(2, email);
            ps.setString(3, telephone);
            ps.setString(4, type_client);
            ps.setString(5, organisation);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    System.out.println("Nouveau client: " + nom + " (ID: " + id + ")");
                    envoyerNotification("NOUVEAU_CLIENT:" + nom + ":" + id);
                    return id;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur création client: " + e.getMessage());
        }
        return -1;
    }

    @Override
    public void modifierClient(int id, String nom, String email, String telephone, String type_client, String organisation) {
        String sql = "UPDATE client SET nom=?, email=?, telephone=?, type_client=?, organisation=? WHERE client_id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nom); ps.setString(2, email); ps.setString(3, telephone);
            ps.setString(4, type_client); ps.setString(5, organisation); ps.setInt(6, id);
            if (ps.executeUpdate() == 0) throw new RuntimeException("Client introuvable");
            envoyerNotification("CLIENT_MODIFIE:" + nom + ":" + id);
        } catch (SQLException e) {
            throw new RuntimeException("Erreur modification client: " + e.getMessage());
        }
    }

    @Override
    public void supprimerClient(int id) {
        String nom = "Inconnu";
        try {
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement("SELECT nom FROM client WHERE client_id=?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) nom = rs.getString("nom");
                }
            }

            // Annuler les réservations du client
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE reservation SET statut='Annulée' WHERE client_id=? AND statut NOT IN ('Annulée','Terminée')")) {
                ps.setInt(1, id);
                int n = ps.executeUpdate();
                if (n > 0) envoyerNotification("RESERVATIONS_ANNULEES_CLIENT:" + nom + ":" + id + ":" + n);
            }

            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM client WHERE client_id=?")) {
                ps.setInt(1, id);
                if (ps.executeUpdate() == 0) throw new RuntimeException("Client introuvable");
            }

            conn.commit();
            System.out.println("Client supprimé: " + nom + " (ID: " + id + ")");
            envoyerNotification("CLIENT_SUPPRIME:" + nom + ":" + id);
        } catch (Exception e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw new RuntimeException("Impossible de supprimer le client: " + e.getMessage());
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    @Override
    public Client rechercherClient(int id) {
        String sql = "SELECT * FROM client WHERE client_id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Client c = new Client();
                    c.client_id = rs.getInt("client_id");
                    c.nom = rs.getString("nom");
                    c.email = rs.getString("email");
                    c.telephone = rs.getString("telephone");
                    c.type_client = rs.getString("type_client");
                    c.organisation = rs.getString("organisation");
                    return c;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur recherche client: " + e.getMessage());
        }
        throw new RuntimeException("Client non trouvé (ID: " + id + ")");
    }

    @Override
    public Client[] listerTousClients() {
        List<Client> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM client ORDER BY client_id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Client c = new Client();
                c.client_id = rs.getInt("client_id");
                c.nom = rs.getString("nom");
                c.email = rs.getString("email");
                c.telephone = rs.getString("telephone");
                c.type_client = rs.getString("type_client");
                c.organisation = rs.getString("organisation");
                list.add(c);
            }
        } catch (SQLException e) {
            System.err.println("Erreur listing clients: " + e.getMessage());
        }
        return list.toArray(new Client[0]);
    }

    // ==================== RÉSERVATIONS ====================


    @Override
    public int creerReservation(int client_id, int chambre_id, String debut, String fin) {
        try {
            Chambre chambre = rechercherChambre(chambre_id);
            if (!"Disponible".equals(chambre.statut)) {
                throw new RuntimeException("La chambre n'est pas disponible");
            }

            LocalDate d1 = LocalDate.parse(debut);
            LocalDate d2 = LocalDate.parse(fin);
            if (d1.isAfter(d2) || d1.equals(d2)) {
                throw new RuntimeException("Dates invalides");
            }

            long jours = ChronoUnit.DAYS.between(d1, d2);
            double total = jours * chambre.prix_par_jour;

            String sql = "INSERT INTO reservation (client_id, chambre_id, date_debut, date_fin, total, statut) " +
                    "VALUES (?, ?, ?, ?, ?, 'En attente')";

            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, client_id);
                ps.setInt(2, chambre_id);
                ps.setDate(3, Date.valueOf(d1));
                ps.setDate(4, Date.valueOf(d2));
                ps.setDouble(5, total);
                ps.executeUpdate();

                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        int id = rs.getInt(1);
                        majStatutChambre(chambre_id, "Réservée"); // Réserve la chambre
                        System.out.println("Réservation créée (En attente) ID: " + id);
                        envoyerNotification("NOUVELLE_RESERVATION:" + id + ":" + client_id + ":" + chambre_id);
                        return id;
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Erreur création réservation : " + e.getMessage());
        }
        return -1;
    }
    @Override
    public void modifierReservation(int reservation_id, int client_id, int chambre_id, String date_debut, String date_fin) {
        try {
            // Vérifier que la nouvelle chambre existe et est disponible (sauf si c'est la même)
            Chambre nouvelleChambre = rechercherChambre(chambre_id);
            Chambre ancienneChambre = null;

            // Récupérer l'ancienne chambre pour libérer/occuper correctement
            try (PreparedStatement ps = conn.prepareStatement("SELECT chambre_id FROM reservation WHERE reservation_id=?")) {
                ps.setInt(1, reservation_id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int ancienneId = rs.getInt("chambre_id");
                        if (ancienneId != chambre_id) {
                            ancienneChambre = rechercherChambre(ancienneId);
                        }
                    } else {
                        throw new RuntimeException("Réservation introuvable (ID: " + reservation_id + ")");
                    }
                }
            }

            // Vérifier disponibilité de la nouvelle chambre
            if (ancienneChambre == null || ancienneChambre.chambre_id != chambre_id) {
                if (!"Disponible".equals(nouvelleChambre.statut) && !"Réservée".equals(nouvelleChambre.statut)) {
                    throw new RuntimeException("La chambre sélectionnée n'est pas disponible");
                }
            }

            // Valider les dates
            LocalDate d1 = LocalDate.parse(date_debut);
            LocalDate d2 = LocalDate.parse(date_fin);
            if (d1.isAfter(d2) || d1.equals(d2)) {
                throw new RuntimeException("Dates invalides : la date de fin doit être après la date de début");
            }

            long jours = ChronoUnit.DAYS.between(d1, d2);
            double total = jours * nouvelleChambre.prix_par_jour;

            // Mise à jour de la réservation
            String sql = "UPDATE reservation SET client_id=?, chambre_id=?, date_debut=?, date_fin=?, total=? WHERE reservation_id=?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, client_id);
                ps.setInt(2, chambre_id);
                ps.setDate(3, Date.valueOf(d1));
                ps.setDate(4, Date.valueOf(d2));
                ps.setDouble(5, total);
                ps.setInt(6, reservation_id);

                int rows = ps.executeUpdate();
                if (rows == 0) {
                    throw new RuntimeException("Réservation non trouvée ou déjà modifiée");
                }
            }

            // Gestion des statuts des chambres
            if (ancienneChambre != null && ancienneChambre.chambre_id != chambre_id) {
                majStatutChambre(ancienneChambre.chambre_id, "Disponible");  // Libérer l'ancienne
            }
            majStatutChambre(chambre_id, "Réservée");  // Réserver la nouvelle

            System.out.println("Réservation modifiée avec succès (ID: " + reservation_id + ")");
            envoyerNotification("RESERVATION_MODIFIEE:" + reservation_id + ":" + client_id + ":" + chambre_id);

        } catch (Exception e) {
            System.err.println("Erreur modification réservation: " + e.getMessage());
            throw new RuntimeException("Impossible de modifier la réservation : " + e.getMessage());
        }
    }

    @Override
    public void annulerReservation(int resa_id) {
        try {
            int chambreId = 0;
            try (PreparedStatement ps = conn.prepareStatement("SELECT chambre_id FROM reservation WHERE reservation_id=?")) {
                ps.setInt(1, resa_id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) chambreId = rs.getInt("chambre_id");
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE reservation SET statut='Annulée' WHERE reservation_id=?")) {
                ps.setInt(1, resa_id);
                if (ps.executeUpdate() == 0) throw new RuntimeException("Réservation introuvable");
            }

            if (chambreId > 0) majStatutChambre(chambreId, "Disponible");

            envoyerNotification("RESERVATION_ANNULEE:" + resa_id + ":" + chambreId);
        } catch (SQLException e) {
            throw new RuntimeException("Erreur annulation réservation: " + e.getMessage());
        }
    }

    @Override
    public boolean majStatutReservation(int reservation_id, String nouveau_statut) {
        try {
            // Récupérer la réservation
            String sqlGet = "SELECT chambre_id, statut FROM reservation WHERE reservation_id = ?";
            int chambreId = 0;
            String ancienStatut = "";

            try (PreparedStatement ps = conn.prepareStatement(sqlGet)) {
                ps.setInt(1, reservation_id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        chambreId = rs.getInt("chambre_id");
                        ancienStatut = rs.getString("statut");
                    } else {
                        return false; // Réservation introuvable
                    }
                }
            }

            // Mettre à jour le statut
            String sqlUpdate = "UPDATE reservation SET statut = ? WHERE reservation_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlUpdate)) {
                ps.setString(1, nouveau_statut);
                ps.setInt(2, reservation_id);
                if (ps.executeUpdate() == 0) return false;
            }

            // Mise à jour automatique du statut de la chambre
            switch (nouveau_statut) {
                case "Confirmée":
                    majStatutChambre(chambreId, "Réservée");
                    break;
                case "Annulée":
                case "Terminée":
                    majStatutChambre(chambreId, "Disponible");
                    break;
                // "En attente" → ne change pas la chambre (déjà réservée)
            }

            System.out.println("Réservation " + reservation_id + " → " + nouveau_statut);
            envoyerNotification("RESERVATION_STATUT:" + reservation_id + ":" + nouveau_statut);

            return true;

        } catch (SQLException e) {
            System.err.println("Erreur majStatutReservation : " + e.getMessage());
            return false;
        }
    }

    @Override
    public Reservation[] historiqueClient(int client_id) {
        return chargerReservations("SELECT * FROM reservation WHERE client_id=? ORDER BY reservation_id DESC", client_id);
    }

    @Override
    public Reservation[] reservationsChambre(int chambre_id) {
        return chargerReservations("SELECT * FROM reservation WHERE chambre_id=? ORDER BY reservation_id DESC", chambre_id);
    }

    // Méthode bonus très utile (déjà dans le .idl)
    @Override
    public Reservation[] toutesLesReservations() {
        return chargerReservations("SELECT * FROM reservation ORDER BY reservation_id DESC", -1);
    }

    private Reservation[] chargerReservations(String sql, int param) {
        List<Reservation> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (param != -1) ps.setInt(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Reservation r = new Reservation();
                    r.reservation_id = rs.getInt("reservation_id");
                    r.client_id = rs.getInt("client_id");
                    r.chambre_id = rs.getInt("chambre_id");
                    r.date_debut = rs.getDate("date_debut").toString();
                    r.date_fin = rs.getDate("date_fin").toString();
                    r.total = rs.getDouble("total");
                    r.statut = rs.getString("statut");
                    list.add(r);
                }
            }
        } catch (SQLException e) {
            System.err.println("Erreur chargement réservations: " + e.getMessage());
        }
        return list.toArray(new Reservation[0]);
    }
}