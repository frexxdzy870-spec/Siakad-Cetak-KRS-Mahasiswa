import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

class MysqlStorageHelper {
    private final String url = setting("siakad.mysql.url", "MYSQL_URL",
            "jdbc:mysql://localhost:3306/siakad_db?serverTimezone=UTC&useUnicode=true&characterEncoding=UTF-8");
    private final String user = setting("siakad.mysql.user", "MYSQL_USER", "root");
    private final String password = setting("siakad.mysql.password", "MYSQL_PASSWORD", "");

    synchronized void initialize(ArrayList<JadwalKuliah> jadwalAwal) throws IOException {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS mahasiswa (
                        nim VARCHAR(100) NOT NULL PRIMARY KEY,
                        nama VARCHAR(255) NOT NULL,
                        nilai DECIMAL(5,2) NOT NULL,
                        foto TEXT NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS jadwal_kuliah (
                        mata_kuliah VARCHAR(255) NOT NULL PRIMARY KEY,
                        hari VARCHAR(20) NOT NULL,
                        jam VARCHAR(50) NOT NULL,
                        sks INT NOT NULL,
                        ruang VARCHAR(100) NOT NULL,
                        dosen VARCHAR(255) NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS krs (
                        nim VARCHAR(100) NOT NULL,
                        mata_kuliah VARCHAR(255) NOT NULL,
                        sks INT NOT NULL,
                        PRIMARY KEY (nim, mata_kuliah),
                        CONSTRAINT fk_krs_mahasiswa FOREIGN KEY (nim)
                            REFERENCES mahasiswa(nim) ON DELETE CASCADE,
                        CONSTRAINT fk_krs_jadwal FOREIGN KEY (mata_kuliah)
                            REFERENCES jadwal_kuliah(mata_kuliah)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            try (ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM jadwal_kuliah")) {
                result.next();
                if (result.getInt(1) == 0) {
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO jadwal_kuliah (mata_kuliah, hari, jam, sks, ruang, dosen) "
                                    + "VALUES (?, ?, ?, ?, ?, ?)")) {
                        for (JadwalKuliah jadwal : jadwalAwal) {
                            insert.setString(1, jadwal.mataKuliah);
                            insert.setString(2, jadwal.hari);
                            insert.setString(3, jadwal.jam);
                            insert.setInt(4, jadwal.sks);
                            insert.setString(5, jadwal.ruang);
                            insert.setString(6, jadwal.dosen);
                            insert.addBatch();
                        }
                        insert.executeBatch();
                    }
                }
            }
        } catch (SQLException ex) {
            throw databaseError("Inisialisasi database MySQL gagal", ex);
        }
    }

    synchronized ArrayList<JadwalKuliah> loadJadwal() throws IOException {
        ArrayList<JadwalKuliah> jadwalList = new ArrayList<>();
        String sql = "SELECT hari, jam, mata_kuliah, sks, ruang, dosen FROM jadwal_kuliah";
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                jadwalList.add(new JadwalKuliah(result.getString("hari"), result.getString("jam"),
                        result.getString("mata_kuliah"), result.getInt("sks"), result.getString("ruang"),
                        result.getString("dosen")));
            }
            jadwalList.sort(Comparator.comparing((JadwalKuliah jadwal) -> urutanHari(jadwal.hari))
                    .thenComparing(jadwal -> jadwal.jam));
            return jadwalList;
        } catch (SQLException ex) {
            throw databaseError("Jadwal kuliah gagal dimuat", ex);
        }
    }

    synchronized ArrayList<Mahasiswa> loadMahasiswa() throws IOException {
        ArrayList<Mahasiswa> mahasiswaList = new ArrayList<>();
        String sql = "SELECT nim, nama, nilai FROM mahasiswa ORDER BY nama";
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                mahasiswaList.add(new Mahasiswa(result.getString("nim"), result.getString("nama"),
                        result.getDouble("nilai")));
            }
            return mahasiswaList;
        } catch (SQLException ex) {
            throw databaseError("Data mahasiswa gagal dimuat", ex);
        }
    }

    synchronized Map<String, ArrayList<KrsItem>> loadKrs() throws IOException {
        Map<String, ArrayList<KrsItem>> hasil = new HashMap<>();
        Map<String, JadwalKuliah> jadwalByName = new HashMap<>();
        for (JadwalKuliah jadwal : loadJadwal()) {
            jadwalByName.put(jadwal.mataKuliah, jadwal);
        }

        String sql = "SELECT nim, mata_kuliah, sks FROM krs";
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                String mataKuliah = result.getString("mata_kuliah");
                int sks = result.getInt("sks");
                hasil.computeIfAbsent(result.getString("nim"), key -> new ArrayList<>())
                        .add(new KrsItem(mataKuliah + " (" + sks + " SKS)", sks));
            }
        } catch (SQLException ex) {
            throw databaseError("Data KRS gagal dimuat", ex);
        }
        Comparator<KrsItem> urutanJadwal = Comparator.comparing(item -> {
            JadwalKuliah jadwal = jadwalByName.get(namaMataKuliah(item.mataKuliah));
            return jadwal == null ? "\uffff" : urutanHari(jadwal.hari) + "\u0000" + jadwal.jam;
        });
        hasil.values().forEach(items -> items.sort(urutanJadwal));
        return hasil;
    }

    synchronized Map<String, String> loadFoto() throws IOException {
        Map<String, String> hasil = new HashMap<>();
        String sql = "SELECT nim, foto FROM mahasiswa WHERE foto <> ''";
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                hasil.put(result.getString("nim"), result.getString("foto"));
            }
            return hasil;
        } catch (SQLException ex) {
            throw databaseError("Foto mahasiswa gagal dimuat", ex);
        }
    }

    synchronized void simpanMahasiswa(Mahasiswa mahasiswa) throws IOException {
        String sql = "INSERT INTO mahasiswa (nim, nama, nilai, foto) VALUES (?, ?, ?, '')";
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, mahasiswa.getNim());
            statement.setString(2, mahasiswa.getNama());
            statement.setDouble(3, mahasiswa.getNilai());
            statement.executeUpdate();
        } catch (SQLException ex) {
            if ("23000".equals(ex.getSQLState())) {
                throw new IOException("NIM tersebut sudah terdaftar.", ex);
            }
            throw databaseError("Data mahasiswa gagal disimpan", ex);
        }
    }

    synchronized void tambahKrs(String nim, String mataKuliah, int sks) throws IOException {
        String nama = namaMataKuliah(mataKuliah);
        String sql = "INSERT INTO krs (nim, mata_kuliah, sks) VALUES (?, ?, ?)";
        try (Connection connection = openConnection()) {
            requireStudent(connection, nim);
            requireSchedule(connection, nama);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, nim);
                statement.setString(2, nama);
                statement.setInt(3, sks);
                statement.executeUpdate();
            }
        } catch (SQLException ex) {
            if ("23000".equals(ex.getSQLState())) {
                throw new IOException("Mata kuliah tersebut sudah terdaftar di KRS.", ex);
            }
            throw databaseError("Mata kuliah gagal ditambahkan ke KRS", ex);
        }
    }

    synchronized void editKrs(String nim, String mataKuliahLama, String mataKuliahBaru, int sks)
            throws IOException {
        String sql = "UPDATE krs SET mata_kuliah = ?, sks = ? WHERE nim = ? AND mata_kuliah = ?";
        try (Connection connection = openConnection()) {
            requireSchedule(connection, namaMataKuliah(mataKuliahBaru));
            requireKrs(connection, nim, namaMataKuliah(mataKuliahLama));
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, namaMataKuliah(mataKuliahBaru));
                statement.setInt(2, sks);
                statement.setString(3, nim);
                statement.setString(4, namaMataKuliah(mataKuliahLama));
                statement.executeUpdate();
            }
        } catch (SQLException ex) {
            if ("23000".equals(ex.getSQLState())) {
                throw new IOException("Mata kuliah tersebut sudah terdaftar di KRS.", ex);
            }
            throw databaseError("Data KRS gagal diedit", ex);
        }
    }

    synchronized void hapusKrs(String nim, String mataKuliah) throws IOException {
        String sql = "DELETE FROM krs WHERE nim = ? AND mata_kuliah = ?";
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, nim);
            statement.setString(2, namaMataKuliah(mataKuliah));
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw databaseError("Data KRS gagal dihapus", ex);
        }
    }

    synchronized void simpanFoto(String nim, String path) throws IOException {
        String sql = "UPDATE mahasiswa SET foto = ? WHERE nim = ?";
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, path);
            statement.setString(2, nim);
            if (statement.executeUpdate() == 0) {
                throw new IOException("Data mahasiswa untuk foto tidak ditemukan.");
            }
        } catch (SQLException ex) {
            throw databaseError("Foto mahasiswa gagal disimpan", ex);
        }
    }

    synchronized void hapusMahasiswa(String nim) throws IOException {
        String sql = "DELETE FROM mahasiswa WHERE nim = ?";
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, nim);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw databaseError("Data mahasiswa gagal dihapus", ex);
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private void requireStudent(Connection connection, String nim) throws SQLException, IOException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM mahasiswa WHERE nim = ?")) {
            statement.setString(1, nim);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IOException("Data mahasiswa tidak ditemukan.");
                }
            }
        }
    }

    private void requireSchedule(Connection connection, String mataKuliah) throws SQLException, IOException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM jadwal_kuliah WHERE mata_kuliah = ?")) {
            statement.setString(1, mataKuliah);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IOException("Mata kuliah tidak ditemukan pada jadwal.");
                }
            }
        }
    }

    private void requireKrs(Connection connection, String nim, String mataKuliah) throws SQLException, IOException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM krs WHERE nim = ? AND mata_kuliah = ?")) {
            statement.setString(1, nim);
            statement.setString(2, mataKuliah);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IOException("Data KRS yang akan diedit tidak ditemukan.");
                }
            }
        }
    }

    private IOException databaseError(String message, SQLException cause) {
        return new IOException(message + ": " + cause.getMessage(), cause);
    }

    private static String setting(String property, String environment, String fallback) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(environment);
        }
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int urutanHari(String hari) {
        switch (hari) {
            case "Senin":
                return 1;
            case "Selasa":
                return 2;
            case "Rabu":
                return 3;
            case "Kamis":
                return 4;
            case "Jumat":
                return 5;
            case "Sabtu":
                return 6;
            case "Minggu":
                return 7;
            default:
                return 8;
        }
    }

    private static String namaMataKuliah(String label) {
        int suffix = label.lastIndexOf(" (");
        return suffix < 0 ? label : label.substring(0, suffix);
    }
}
