import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

class StorageHelper {
    private final Path filePath = Paths.get("siakad-data.properties").toAbsolutePath();
    private Properties data = new Properties();

    synchronized void initialize(ArrayList<JadwalKuliah> jadwalAwal) throws IOException {
        if (Files.exists(filePath)) {
            try (var input = Files.newInputStream(filePath)) {
                data.load(input);
            }
            return;
        }

        Properties initialData = new Properties();
        initialData.setProperty("mahasiswa.count", "0");
        initialData.setProperty("krs.count", "0");
        initialData.setProperty("jadwal.count", Integer.toString(jadwalAwal.size()));
        for (int i = 0; i < jadwalAwal.size(); i++) {
            JadwalKuliah jadwal = jadwalAwal.get(i);
            String prefix = "jadwal." + i + ".";
            initialData.setProperty(prefix + "hari", jadwal.hari);
            initialData.setProperty(prefix + "jam", jadwal.jam);
            initialData.setProperty(prefix + "mataKuliah", jadwal.mataKuliah);
            initialData.setProperty(prefix + "sks", Integer.toString(jadwal.sks));
            initialData.setProperty(prefix + "ruang", jadwal.ruang);
            initialData.setProperty(prefix + "dosen", jadwal.dosen);
        }
        save(initialData);
    }

    synchronized ArrayList<JadwalKuliah> loadJadwal() throws IOException {
        ArrayList<JadwalKuliah> jadwalList = new ArrayList<>();
        int count = getCount("jadwal");
        for (int i = 0; i < count; i++) {
            String prefix = "jadwal." + i + ".";
            jadwalList.add(new JadwalKuliah(
                    required(prefix + "hari"),
                    required(prefix + "jam"),
                    required(prefix + "mataKuliah"),
                    parseInt(prefix + "sks"),
                    required(prefix + "ruang"),
                    required(prefix + "dosen")));
        }
        return jadwalList;
    }

    synchronized ArrayList<Mahasiswa> loadMahasiswa() throws IOException {
        ArrayList<Mahasiswa> mahasiswaList = new ArrayList<>();
        int count = getCount("mahasiswa");
        for (int i = 0; i < count; i++) {
            String prefix = "mahasiswa." + i + ".";
            double nilai;
            try {
                nilai = Double.parseDouble(required(prefix + "nilai"));
            } catch (NumberFormatException ex) {
                throw new IOException("Nilai mahasiswa pada file penyimpanan tidak valid.", ex);
            }
            mahasiswaList.add(new Mahasiswa(required(prefix + "nim"), required(prefix + "nama"), nilai));
        }
        mahasiswaList.sort(Comparator.comparing(Mahasiswa::getNama));
        return mahasiswaList;
    }

    synchronized Map<String, ArrayList<KrsItem>> loadKrs() throws IOException {
        Map<String, ArrayList<KrsItem>> hasil = new HashMap<>();
        Map<String, JadwalKuliah> jadwalByName = new HashMap<>();
        for (JadwalKuliah jadwal : loadJadwal()) {
            jadwalByName.put(jadwal.mataKuliah, jadwal);
        }

        int count = getCount("krs");
        for (int i = 0; i < count; i++) {
            String prefix = "krs." + i + ".";
            String nim = required(prefix + "nim");
            String mataKuliah = required(prefix + "mataKuliah");
            int sks = parseInt(prefix + "sks");
            hasil.computeIfAbsent(nim, key -> new ArrayList<>())
                    .add(new KrsItem(mataKuliah + " (" + sks + " SKS)", sks));
        }
        Comparator<KrsItem> urutanJadwal = Comparator.comparing(item -> {
            JadwalKuliah jadwal = jadwalByName.get(namaMataKuliah(item.mataKuliah));
            return jadwal == null ? "\uffff" : jadwal.hari + "\u0000" + jadwal.jam;
        });
        hasil.values().forEach(items -> items.sort(urutanJadwal));
        return hasil;
    }

    synchronized Map<String, String> loadFoto() throws IOException {
        Map<String, String> hasil = new HashMap<>();
        int count = getCount("mahasiswa");
        for (int i = 0; i < count; i++) {
            String prefix = "mahasiswa." + i + ".";
            String foto = data.getProperty(prefix + "foto");
            if (foto != null && !foto.isEmpty()) {
                hasil.put(required(prefix + "nim"), foto);
            }
        }
        return hasil;
    }

    synchronized void simpanMahasiswa(Mahasiswa mahasiswa) throws IOException {
        ArrayList<StudentRecord> mahasiswaList = readMahasiswa();
        for (StudentRecord record : mahasiswaList) {
            if (record.nim.equals(mahasiswa.getNim())) {
                throw new IOException("NIM tersebut sudah terdaftar.");
            }
        }
        mahasiswaList.add(new StudentRecord(mahasiswa.getNim(), mahasiswa.getNama(),
                Double.toString(mahasiswa.getNilai()), ""));
        Properties updated = copyData();
        writeMahasiswa(updated, mahasiswaList);
        save(updated);
    }

    synchronized void tambahKrs(String nim, String mataKuliah, int sks) throws IOException {
        requireStudent(nim);
        String nama = namaMataKuliah(mataKuliah);
        requireSchedule(nama);
        ArrayList<KrsRecord> items = readKrs();
        for (KrsRecord item : items) {
            if (item.nim.equals(nim) && item.mataKuliah.equals(nama)) {
                throw new IOException("Mata kuliah tersebut sudah terdaftar di KRS.");
            }
        }
        items.add(new KrsRecord(nim, nama, sks));
        Properties updated = copyData();
        writeKrs(updated, items);
        save(updated);
    }

    synchronized void editKrs(String nim, String mataKuliahLama, String mataKuliahBaru, int sks)
            throws IOException {
        String lama = namaMataKuliah(mataKuliahLama);
        String baru = namaMataKuliah(mataKuliahBaru);
        requireSchedule(baru);
        ArrayList<KrsRecord> items = readKrs();
        int index = -1;
        for (int i = 0; i < items.size(); i++) {
            KrsRecord item = items.get(i);
            if (item.nim.equals(nim) && item.mataKuliah.equals(lama)) {
                index = i;
            } else if (item.nim.equals(nim) && item.mataKuliah.equals(baru)) {
                throw new IOException("Mata kuliah tersebut sudah terdaftar di KRS.");
            }
        }
        if (index < 0) {
            throw new IOException("Data KRS yang akan diedit tidak ditemukan.");
        }
        items.set(index, new KrsRecord(nim, baru, sks));
        Properties updated = copyData();
        writeKrs(updated, items);
        save(updated);
    }

    synchronized void hapusKrs(String nim, String mataKuliah) throws IOException {
        String nama = namaMataKuliah(mataKuliah);
        ArrayList<KrsRecord> items = readKrs();
        items.removeIf(item -> item.nim.equals(nim) && item.mataKuliah.equals(nama));
        Properties updated = copyData();
        writeKrs(updated, items);
        save(updated);
    }

    synchronized void simpanFoto(String nim, String path) throws IOException {
        ArrayList<StudentRecord> mahasiswaList = readMahasiswa();
        boolean ditemukan = false;
        for (StudentRecord record : mahasiswaList) {
            if (record.nim.equals(nim)) {
                record.foto = path;
                ditemukan = true;
                break;
            }
        }
        if (!ditemukan) {
            throw new IOException("Data mahasiswa untuk foto tidak ditemukan.");
        }
        Properties updated = copyData();
        writeMahasiswa(updated, mahasiswaList);
        save(updated);
    }

    synchronized void hapusMahasiswa(String nim) throws IOException {
        ArrayList<StudentRecord> mahasiswaList = readMahasiswa();
        mahasiswaList.removeIf(record -> record.nim.equals(nim));
        ArrayList<KrsRecord> krsList = readKrs();
        krsList.removeIf(item -> item.nim.equals(nim));
        Properties updated = copyData();
        writeMahasiswa(updated, mahasiswaList);
        writeKrs(updated, krsList);
        save(updated);
    }

    private ArrayList<StudentRecord> readMahasiswa() throws IOException {
        ArrayList<StudentRecord> mahasiswaList = new ArrayList<>();
        int count = getCount("mahasiswa");
        for (int i = 0; i < count; i++) {
            String prefix = "mahasiswa." + i + ".";
            mahasiswaList.add(new StudentRecord(required(prefix + "nim"), required(prefix + "nama"),
                    required(prefix + "nilai"), data.getProperty(prefix + "foto", "")));
        }
        return mahasiswaList;
    }

    private ArrayList<KrsRecord> readKrs() throws IOException {
        ArrayList<KrsRecord> items = new ArrayList<>();
        int count = getCount("krs");
        for (int i = 0; i < count; i++) {
            String prefix = "krs." + i + ".";
            items.add(new KrsRecord(required(prefix + "nim"), required(prefix + "mataKuliah"),
                    parseInt(prefix + "sks")));
        }
        return items;
    }

    private void requireStudent(String nim) throws IOException {
        for (StudentRecord record : readMahasiswa()) {
            if (record.nim.equals(nim)) {
                return;
            }
        }
        throw new IOException("Data mahasiswa tidak ditemukan.");
    }

    private void requireSchedule(String mataKuliah) throws IOException {
        for (JadwalKuliah jadwal : loadJadwal()) {
            if (jadwal.mataKuliah.equals(mataKuliah)) {
                return;
            }
        }
        throw new IOException("Mata kuliah tidak ditemukan pada jadwal.");
    }

    private int getCount(String section) throws IOException {
        String value = required(section + ".count");
        try {
            int count = Integer.parseInt(value);
            if (count < 0) {
                throw new NumberFormatException("negative count");
            }
            return count;
        } catch (NumberFormatException ex) {
            throw new IOException("Jumlah data " + section + " pada file penyimpanan tidak valid.", ex);
        }
    }

    private int parseInt(String key) throws IOException {
        try {
            return Integer.parseInt(required(key));
        } catch (NumberFormatException ex) {
            throw new IOException("Data angka pada file penyimpanan tidak valid: " + key, ex);
        }
    }

    private String required(String key) throws IOException {
        String value = data.getProperty(key);
        if (value == null) {
            throw new IOException("Data pada file penyimpanan tidak lengkap: " + key);
        }
        return value;
    }

    private Properties copyData() {
        Properties copy = new Properties();
        copy.putAll(data);
        return copy;
    }

    private void writeMahasiswa(Properties target, ArrayList<StudentRecord> mahasiswaList) {
        removeSection(target, "mahasiswa");
        target.setProperty("mahasiswa.count", Integer.toString(mahasiswaList.size()));
        for (int i = 0; i < mahasiswaList.size(); i++) {
            StudentRecord record = mahasiswaList.get(i);
            String prefix = "mahasiswa." + i + ".";
            target.setProperty(prefix + "nim", record.nim);
            target.setProperty(prefix + "nama", record.nama);
            target.setProperty(prefix + "nilai", record.nilai);
            target.setProperty(prefix + "foto", record.foto);
        }
    }

    private void writeKrs(Properties target, ArrayList<KrsRecord> items) {
        removeSection(target, "krs");
        target.setProperty("krs.count", Integer.toString(items.size()));
        for (int i = 0; i < items.size(); i++) {
            KrsRecord record = items.get(i);
            String prefix = "krs." + i + ".";
            target.setProperty(prefix + "nim", record.nim);
            target.setProperty(prefix + "mataKuliah", record.mataKuliah);
            target.setProperty(prefix + "sks", Integer.toString(record.sks));
        }
    }

    private void removeSection(Properties target, String section) {
        target.keySet().removeIf(key -> key.toString().startsWith(section + "."));
    }

    private void save(Properties updated) throws IOException {
        Path parent = filePath.getParent();
        Files.createDirectories(parent);
        Path temporaryFile = Files.createTempFile(parent, "siakad-data-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporaryFile)) {
                updated.store(output, "SIAKAD local data");
            }
            try {
                Files.move(temporaryFile, filePath, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporaryFile, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
            data = updated;
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private String namaMataKuliah(String label) {
        int suffix = label.lastIndexOf(" (");
        return suffix < 0 ? label : label.substring(0, suffix);
    }

    private static class StudentRecord {
        private final String nim;
        private final String nama;
        private final String nilai;
        private String foto;

        StudentRecord(String nim, String nama, String nilai, String foto) {
            this.nim = nim;
            this.nama = nama;
            this.nilai = nilai;
            this.foto = foto;
        }
    }

    private static class KrsRecord {
        private final String nim;
        private final String mataKuliah;
        private final int sks;

        KrsRecord(String nim, String mataKuliah, int sks) {
            this.nim = nim;
            this.mataKuliah = mataKuliah;
            this.sks = sks;
        }
    }
}
