import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

class Mahasiswa {
    private String nim;
    private String nama;
    private double nilai;

    public Mahasiswa(String nim, String nama, double nilai) {
        this.nim = nim;
        this.nama = nama;
        this.nilai = nilai;
    }

    public String getNim() {
        return nim;
    }

    public String getNama() {
        return nama;
    }

    public double getNilai() {
        return nilai;
    }

    public String getGrade() {
        if (nilai >= 85)
            return "A";
        if (nilai >= 75)
            return "B";
        if (nilai >= 65)
            return "C";
        if (nilai >= 50)
            return "D";
        return "E";
    }

    public String getStatus() {
        return nilai >= 60 ? "Lulus" : "Tidak Lulus";
    }

    @Override
    public String toString() {
        return nim + " - " + nama;
    }
}

class JadwalKuliah {
    String hari, jam, mataKuliah, ruang, dosen;
    int sks;

    public JadwalKuliah(String hari, String jam, String mataKuliah, int sks, String ruang, String dosen) {
        this.hari = hari;
        this.jam = jam;
        this.mataKuliah = mataKuliah;
        this.sks = sks;
        this.ruang = ruang;
        this.dosen = dosen;
    }
}

class KrsItem {
    String mataKuliah;
    int sks;

    KrsItem(String mataKuliah, int sks) {
        this.mataKuliah = mataKuliah;
        this.sks = sks;
    }
}

public class Main extends JFrame {
    private JTextField txtNim, txtNama, txtNilai, txtCari;
    private JTable table, tableJadwal, tableKrs;
    private DefaultTableModel tableModel, tableModelJadwal, tableModelKrs;
    private TableRowSorter<DefaultTableModel> sorter;
    private JLabel lblTotalMhs, lblRataRata, lblLulus;
    private JComboBox<String> cmbMatkul;
    private JComboBox<Mahasiswa> cmbNimKrs;
    private MysqlStorageHelper storage;

    private ArrayList<Mahasiswa> listMahasiswa = new ArrayList<>();
    private ArrayList<JadwalKuliah> listJadwal = new ArrayList<>();
    private Map<String, ArrayList<KrsItem>> dataKrs = new HashMap<>();
    private Map<String, String> fotoMahasiswa = new HashMap<>();

    // Warna Tema Modern
    private final Color COLOR_PRIMARY = new Color(41, 128, 185); // Blue
    private final Color COLOR_SECONDARY = new Color(52, 152, 219); // Light Blue
    private final Color COLOR_BG = new Color(245, 247, 250); // Soft Gray Background
    private final Color COLOR_SUCCESS = new Color(39, 174, 96); // Green
    private final Color COLOR_DANGER = new Color(192, 57, 43); // Red

    public Main() {
        setTitle("Sistem Informasi Akademik (SIAKAD) - Universitas Hasyim Asy'ari");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(COLOR_BG);
        setLayout(new BorderLayout(15, 15));

        // Inisialisasi Data Dummy Jadwal Kuliah
        inisialisasiJadwal();
        try {
            storage = new MysqlStorageHelper();
            storage.initialize(listJadwal);
            listJadwal = storage.loadJadwal();
        } catch (Exception ex) {
            throw new IllegalStateException("Database MySQL tidak dapat dibuka.", ex);
        }

        // 1. Header Panel dengan Logo
        JPanel headerPanel = createHeaderPanel();
        add(headerPanel, BorderLayout.NORTH);

        // 2. Tabbed Pane (Tab 1: Nilai Mahasiswa, Tab 2: KRS & Jadwal)
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 13));

        tabbedPane.addTab("Data & Nilai Mahasiswa", createTabNilai());
        tabbedPane.addTab("KRS & Jadwal Kuliah", createTabKrsJadwal());

        add(tabbedPane, BorderLayout.CENTER);
        muatDataPenyimpanan();
    }

    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 0));
        panel.setBackground(COLOR_PRIMARY);
        panel.setBorder(new EmptyBorder(15, 20, 15, 20));
 
        JLabel logoLabel = new JLabel();
        File logoFile = new File("2.png");
        if (logoFile.exists()) {
            ImageIcon rawIcon = new ImageIcon("2.png");
            Image scaledImg = rawIcon.getImage().getScaledInstance(-1, 55, Image.SCALE_SMOOTH);
            logoLabel.setIcon(new ImageIcon(scaledImg));
        } else {
            logoLabel.setText("[LOGO]");
            logoLabel.setForeground(Color.WHITE);
        }

        JLabel title = new JLabel("SISTEM INFORMASI AKADEMIK (SIAKAD)");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(Color.WHITE);

        JLabel subtitle = new JLabel("UNIVERSITAS HASYIM ASY'ARI");
        subtitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        subtitle.setForeground(new Color(241, 196, 15));

        JPanel textWrapper = new JPanel(new GridLayout(2, 1));
        textWrapper.setOpaque(false);
        textWrapper.add(title);
        textWrapper.add(subtitle);

        panel.add(logoLabel, BorderLayout.WEST);
        panel.add(textWrapper, BorderLayout.CENTER);
        return panel;
    }

    // --- TAB 1: NILAI MAHASISWA ---
    private JPanel createTabNilai() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JPanel formPanel = createFormPanel();
        JPanel tablePanel = createTablePanel();
        JPanel footerPanel = createFooterPanel();

        panel.add(formPanel, BorderLayout.WEST);
        panel.add(tablePanel, BorderLayout.CENTER);
        panel.add(footerPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createFormPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                new EmptyBorder(20, 20, 20, 20)));
        panel.setPreferredSize(new Dimension(280, 0));

        JLabel lblFormTitle = new JLabel("Input Data Mahasiswa");
        lblFormTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblFormTitle.setForeground(COLOR_PRIMARY);
        lblFormTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        txtNim = createStyledTextField();
        txtNama = createStyledTextField();
        txtNilai = createStyledTextField();

        JButton btnSimpan = new JButton("Simpan Data");
        styleButton(btnSimpan, COLOR_PRIMARY, Color.WHITE);
        btnSimpan.addActionListener(e -> tambahData());

        JButton btnReset = new JButton("Reset Form");
        styleButton(btnReset, new Color(149, 165, 166), Color.WHITE);
        btnReset.addActionListener(e -> clearForm());

        JButton btnHapus = new JButton("Hapus Baris Terpilih");
        styleButton(btnHapus, COLOR_DANGER, Color.WHITE);
        btnHapus.addActionListener(e -> hapusData());

        panel.add(lblFormTitle);
        panel.add(Box.createVerticalStrut(15));
        panel.add(createInputGroup("NIM", txtNim));
        panel.add(Box.createVerticalStrut(10));
        panel.add(createInputGroup("Nama Lengkap", txtNama));
        panel.add(Box.createVerticalStrut(10));
        panel.add(createInputGroup("Nilai (0 - 100)", txtNilai));
        panel.add(Box.createVerticalStrut(20));
        panel.add(btnSimpan);
        panel.add(Box.createVerticalStrut(8));
        panel.add(btnReset);
        panel.add(Box.createVerticalStrut(8));
        panel.add(btnHapus);

        return panel;
    }

    private JPanel createTablePanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setOpaque(false);

        JPanel searchWrapper = new JPanel(new BorderLayout());
        searchWrapper.setOpaque(false);
        searchWrapper.setBorder(new EmptyBorder(0, 0, 8, 0));

        JPanel searchPanel = new JPanel(new BorderLayout(10, 0));
        searchPanel.setOpaque(false);

        JLabel lblCari = new JLabel("Cari Data: ");
        lblCari.setFont(new Font("Segoe UI", Font.BOLD, 12));

        txtCari = createStyledTextField();
        txtCari.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                String query = txtCari.getText();
                if (query.trim().length() == 0) {
                    sorter.setRowFilter(null);
                } else {
                    sorter.setRowFilter(RowFilter.regexFilter("(?i)" + query));
                }
            }
        });

        searchPanel.add(lblCari, BorderLayout.WEST);
        searchPanel.add(txtCari, BorderLayout.CENTER);
        searchWrapper.add(searchPanel, BorderLayout.CENTER);

        String[] columns = { "NIM", "Nama Mahasiswa", "Nilai", "Grade", "Status" };
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(tableModel);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.setRowHeight(28);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.getTableHeader().setBackground(new Color(230, 235, 240));
        table.getTableHeader().setForeground(Color.BLACK);
        table.getTableHeader().setPreferredSize(new Dimension(0, 32));
        table.setSelectionBackground(new Color(232, 240, 254));
        table.setSelectionForeground(Color.BLACK);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        table.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        table.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);
        table.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);
        table.getColumnModel().getColumn(4).setCellRenderer(centerRenderer);

        sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(220, 224, 230)));

        panel.add(searchWrapper, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createFooterPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 3, 15, 0));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(10, 0, 0, 0));

        lblTotalMhs = createStatCard("Total Mahasiswa", "0", COLOR_PRIMARY);
        lblRataRata = createStatCard("Rata-Rata Kelas", "0.00", COLOR_SUCCESS);
        lblLulus = createStatCard("Tingkat Kelulusan", "0%", new Color(142, 68, 173));

        panel.add(lblTotalMhs.getParent());
        panel.add(lblRataRata.getParent());
        panel.add(lblLulus.getParent());

        return panel;
    }

    // --- TAB 2: KRS & JADWAL KULIAH ---
    private JPanel createTabKrsJadwal() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 15, 0));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Panel Kiri: Jadwal Kuliah
        JPanel panelJadwal = new JPanel(new BorderLayout(10, 10));
        panelJadwal.setOpaque(false);
        panelJadwal.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 205, 210)),
                " Jadwal Mata Kuliah Umum ", 0, 0, new Font("Segoe UI", Font.BOLD, 13), COLOR_PRIMARY));

        String[] colJadwal = { "Hari", "Jam", "Mata Kuliah", "SKS", "Ruang", "Dosen" };
        tableModelJadwal = new DefaultTableModel(colJadwal, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tableJadwal = new JTable(tableModelJadwal);
        tableJadwal.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        tableJadwal.setRowHeight(25);
        tableJadwal.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
        tableJadwal.getTableHeader().setBackground(new Color(230, 235, 240));
        tableJadwal.getTableHeader().setForeground(Color.BLACK);

        loadDataJadwal();
        panelJadwal.add(new JScrollPane(tableJadwal), BorderLayout.CENTER);

        // Panel Kanan: Form & Tabel KRS Mahasiswa
        JPanel panelKrs = new JPanel(new BorderLayout(10, 10));
        panelKrs.setOpaque(false);
        panelKrs.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 205, 210)),
                " Input & Kartu Rencana Studi (KRS) ", 0, 0, new Font("Segoe UI", Font.BOLD, 13), COLOR_PRIMARY));

        JPanel formKrs = new JPanel(new GridLayout(0, 2, 8, 8));
        formKrs.setOpaque(false);
        formKrs.setBorder(new EmptyBorder(5, 5, 5, 5));

        formKrs.add(new JLabel("Pilih Mahasiswa (NIM - Nama):"));
        cmbNimKrs = new JComboBox<>();
        cmbNimKrs.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setText(value instanceof Mahasiswa ? value.toString() : "Belum ada data mahasiswa");
                return this;
            }
        });
        cmbNimKrs.addActionListener(e -> tampilkanKrsTerpilih());
        formKrs.add(cmbNimKrs);

        formKrs.add(new JLabel("Pilih Mata Kuliah:"));
        cmbMatkul = new JComboBox<>();
        for (JadwalKuliah j : listJadwal) {
            cmbMatkul.addItem(j.mataKuliah + " (" + j.sks + " SKS)");
        }
        formKrs.add(cmbMatkul);

        JButton btnAmbilMatkul = new JButton("Tambah Mata Kuliah");
        styleButton(btnAmbilMatkul, COLOR_SUCCESS, Color.WHITE);
        btnAmbilMatkul.addActionListener(e -> tambahKrs());
        formKrs.add(new JLabel("Aksi KRS:"));
        formKrs.add(btnAmbilMatkul);

        String[] colKrs = { "NIM Mahasiswa", "Nama Mahasiswa", "Mata Kuliah Diambil", "SKS" };
        tableModelKrs = new DefaultTableModel(colKrs, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tableKrs = new JTable(tableModelKrs);
        tableKrs.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tableKrs.setRowHeight(25);
        tableKrs.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
        tableKrs.getTableHeader().setBackground(new Color(230, 235, 240));
        tableKrs.getTableHeader().setForeground(Color.BLACK);

        JButton btnEditKrs = new JButton("Edit Mata Kuliah");
        styleButton(btnEditKrs, COLOR_PRIMARY, Color.WHITE);
        btnEditKrs.addActionListener(e -> editKrs());
        JButton btnHapusKrs = new JButton("Hapus Mata Kuliah");
        styleButton(btnHapusKrs, COLOR_DANGER, Color.WHITE);
        btnHapusKrs.addActionListener(e -> hapusKrs());
        JButton btnFoto = new JButton("Ganti Foto");
        styleButton(btnFoto, new Color(127, 140, 141), Color.WHITE);
        btnFoto.addActionListener(e -> pilihFoto());
        JButton btnPreview = new JButton("Preview / Cetak KRS");
        styleButton(btnPreview, COLOR_PRIMARY, Color.WHITE);
        btnPreview.addActionListener(e -> previewKrs());

        JPanel aksiKrs = new JPanel(new GridLayout(2, 2, 8, 8));
        aksiKrs.setOpaque(false);
        aksiKrs.add(btnEditKrs);
        aksiKrs.add(btnHapusKrs);
        aksiKrs.add(btnFoto);
        aksiKrs.add(btnPreview);

        JPanel krsContent = new JPanel(new BorderLayout(10, 10));
        krsContent.setOpaque(false);
        krsContent.add(formKrs, BorderLayout.NORTH);
        krsContent.add(new JScrollPane(tableKrs), BorderLayout.CENTER);
        krsContent.add(aksiKrs, BorderLayout.SOUTH);

        panelKrs.add(krsContent, BorderLayout.CENTER);

        panel.add(panelJadwal);
        panel.add(panelKrs);

        return panel;
    }

    private void inisialisasiJadwal() {
        listJadwal.add(new JadwalKuliah("Senin", "08:00 - 10:30", "Pemrograman Berorientasi Objek", 3, "Lab Komputer 1",
                "Dr. H. Ahmad, M.Kom"));
        listJadwal.add(new JadwalKuliah("Selasa", "10:30 - 13:00", "Struktur Data & Algoritma", 3, "R. 302",
                "Siti Aminah, M.T"));
        listJadwal.add(new JadwalKuliah("Rabu", "08:00 - 10:30", "Basis Data Relasional", 3, "Lab Komputer 2",
                "Budi Santoso, M.Cs"));
        listJadwal.add(new JadwalKuliah("Kamis", "13:00 - 15:30", "Jaringan Komputer Dasar", 3, "R. 304",
                "Zainal Arifin, M.Kom"));
        listJadwal.add(new JadwalKuliah("Jumat", "08:00 - 09:40", "Pendidikan Pancasila & Kewarganegaraan", 2, "R. 101",
                "Dr. M. Hasyim, M.Pd"));
    }

    private void loadDataJadwal() {
        tableModelJadwal.setRowCount(0);
        for (JadwalKuliah j : listJadwal) {
            tableModelJadwal.addRow(new Object[] { j.hari, j.jam, j.mataKuliah, j.sks, j.ruang, j.dosen });
        }
    }

    private JPanel createInputGroup(String labelText, JTextField textField) {
        JPanel group = new JPanel(new BorderLayout(5, 5));
        group.setOpaque(false);
        group.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel(labelText);
        label.setFont(new Font("Segoe UI", Font.BOLD, 12));
        label.setForeground(new Color(44, 62, 80));

        group.add(label, BorderLayout.NORTH);
        group.add(textField, BorderLayout.CENTER);
        return group;
    }

    private JTextField createStyledTextField() {
        JTextField field = new JTextField();
        field.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        field.setPreferredSize(new Dimension(240, 32));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(189, 195, 199), 1),
                new EmptyBorder(5, 8, 5, 8)));
        return field;
    }

    private void styleButton(JButton btn, Color bgColor, Color fgColor) {
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setBackground(bgColor);
        btn.setForeground(fgColor);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setMaximumSize(new Dimension(300, 35));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private JLabel createStatCard(String titleText, String initialValue, Color accentColor) {
        JPanel card = new JPanel(new BorderLayout(5, 5));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 5, 0, 0, accentColor),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                        new EmptyBorder(10, 15, 10, 15))));

        JLabel title = new JLabel(titleText);
        title.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        title.setForeground(new Color(127, 140, 141));

        JLabel value = new JLabel(initialValue);
        value.setFont(new Font("Segoe UI", Font.BOLD, 20));
        value.setForeground(new Color(44, 62, 80));

        card.add(title, BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);

        return value;
    }

    private void tambahData() {
        String nim = txtNim.getText().trim();
        String nama = txtNama.getText().trim();
        String strNilai = txtNilai.getText().trim();

        if (nim.isEmpty() || nama.isEmpty() || strNilai.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Semua field harus diisi!", "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }

        double nilai;
        try {
            nilai = Double.parseDouble(strNilai);
            if (nilai < 0 || nilai > 100) {
                JOptionPane.showMessageDialog(this, "Nilai harus berada di rentang 0 - 100!", "Error Nilai",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Nilai harus berupa angka!", "Error Format", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Mahasiswa mhs = new Mahasiswa(nim, nama, nilai);
        try {
            storage.simpanMahasiswa(mhs);
        } catch (Exception ex) {
            tampilkanErrorPenyimpanan(ex);
            return;
        }
        listMahasiswa.add(mhs);

        tableModel.addRow(new Object[] {
                mhs.getNim(), mhs.getNama(), String.format("%.2f", mhs.getNilai()), mhs.getGrade(), mhs.getStatus()
        });

        // Update pilihan NIM di tab KRS secara otomatis
        cmbNimKrs.addItem(mhs);
        dataKrs.put(nim, new ArrayList<>());

        clearForm();
        updateStatistik();
        JOptionPane.showMessageDialog(this, "Data Mahasiswa Berhasil Ditambahkan!", "Sukses",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void tambahKrs() {
        Mahasiswa mahasiswa = (Mahasiswa) cmbNimKrs.getSelectedItem();
        if (mahasiswa == null) {
            JOptionPane.showMessageDialog(this,
                    "Belum ada data mahasiswa terdaftar! Silakan input data mahasiswa terlebih dahulu pada Tab 1.",
                    "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String selectedMatkul = (String) cmbMatkul.getSelectedItem();
        if (selectedMatkul == null)
            return;
        ArrayList<KrsItem> items = dataKrs.get(mahasiswa.getNim());
        if (items == null) {
            items = new ArrayList<>();
            dataKrs.put(mahasiswa.getNim(), items);
        }
        for (KrsItem item : items) {
            if (item.mataKuliah.equals(selectedMatkul)) {
                JOptionPane.showMessageDialog(this, "Mata kuliah tersebut sudah ada di KRS.", "Peringatan",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        try {
            storage.tambahKrs(mahasiswa.getNim(), selectedMatkul, getSks(selectedMatkul));
        } catch (Exception ex) {
            tampilkanErrorPenyimpanan(ex);
            return;
        }
        items.add(new KrsItem(selectedMatkul, getSks(selectedMatkul)));
        tampilkanKrsTerpilih();
        JOptionPane.showMessageDialog(this, "Mata kuliah berhasil ditambahkan ke KRS mahasiswa!", "Sukses",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private int getSks(String mataKuliah) {
        for (JadwalKuliah jadwal : listJadwal) {
            String label = jadwal.mataKuliah + " (" + jadwal.sks + " SKS)";
            if (label.equals(mataKuliah))
                return jadwal.sks;
        }
        return 0;
    }

    private ArrayList<KrsItem> getKrsTerpilih() {
        Mahasiswa mahasiswa = (Mahasiswa) cmbNimKrs.getSelectedItem();
        return mahasiswa == null ? null : dataKrs.get(mahasiswa.getNim());
    }

    private void tampilkanKrsTerpilih() {
        if (tableModelKrs == null)
            return;
        tableModelKrs.setRowCount(0);
        Mahasiswa mahasiswa = (Mahasiswa) cmbNimKrs.getSelectedItem();
        ArrayList<KrsItem> items = getKrsTerpilih();
        if (mahasiswa == null || items == null)
            return;
        for (KrsItem item : items) {
            tableModelKrs.addRow(new Object[] { mahasiswa.getNim(), mahasiswa.getNama(), item.mataKuliah,
                    item.sks + " SKS" });
        }
    }

    private void editKrs() {
        int row = tableKrs.getSelectedRow();
        ArrayList<KrsItem> items = getKrsTerpilih();
        if (row < 0 || items == null || row >= items.size()) {
            JOptionPane.showMessageDialog(this, "Pilih mata kuliah pada tabel KRS yang akan diedit.", "Peringatan",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        String pilihan = (String) JOptionPane.showInputDialog(this, "Pilih mata kuliah pengganti:", "Edit KRS",
                JOptionPane.PLAIN_MESSAGE, null, cmbMatkul.getModel().getElementAt(0) == null ? new String[0]
                        : getPilihanMatkul(),
                items.get(row).mataKuliah);
        if (pilihan == null)
            return;
        for (int i = 0; i < items.size(); i++) {
            if (i != row && items.get(i).mataKuliah.equals(pilihan)) {
                JOptionPane.showMessageDialog(this, "Mata kuliah tersebut sudah ada di KRS.", "Peringatan",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        try {
            storage.editKrs(((Mahasiswa) cmbNimKrs.getSelectedItem()).getNim(), items.get(row).mataKuliah,
                    pilihan, getSks(pilihan));
        } catch (Exception ex) {
            tampilkanErrorPenyimpanan(ex);
            return;
        }
        items.set(row, new KrsItem(pilihan, getSks(pilihan)));
        tampilkanKrsTerpilih();
    }

    private String[] getPilihanMatkul() {
        String[] pilihan = new String[cmbMatkul.getItemCount()];
        for (int i = 0; i < pilihan.length; i++)
            pilihan[i] = cmbMatkul.getItemAt(i);
        return pilihan;
    }

    private void hapusKrs() {
        int row = tableKrs.getSelectedRow();
        ArrayList<KrsItem> items = getKrsTerpilih();
        if (row < 0 || items == null || row >= items.size()) {
            JOptionPane.showMessageDialog(this, "Pilih mata kuliah pada tabel KRS yang akan dihapus.", "Peringatan",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        Mahasiswa mahasiswa = (Mahasiswa) cmbNimKrs.getSelectedItem();
        try {
            storage.hapusKrs(mahasiswa.getNim(), items.get(row).mataKuliah);
        } catch (Exception ex) {
            tampilkanErrorPenyimpanan(ex);
            return;
        }
        items.remove(row);
        tampilkanKrsTerpilih();
    }

    private void pilihFoto() {
        Mahasiswa mahasiswa = (Mahasiswa) cmbNimKrs.getSelectedItem();
        if (mahasiswa == null) {
            JOptionPane.showMessageDialog(this, "Pilih mahasiswa terlebih dahulu.", "Peringatan",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Pilih foto mahasiswa");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            String path = chooser.getSelectedFile().getAbsolutePath();
            try {
                storage.simpanFoto(mahasiswa.getNim(), path);
                fotoMahasiswa.put(mahasiswa.getNim(), path);
            } catch (Exception ex) {
                tampilkanErrorPenyimpanan(ex);
            }
        }
    }

    private JPanel buatDokumenKrs(Mahasiswa mahasiswa) {
        JPanel dokumen = new JPanel(new BorderLayout(12, 12));
        dokumen.setBackground(Color.WHITE);
        dokumen.setBorder(new EmptyBorder(28, 32, 28, 32));
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);

        JLabel logoLabel = new JLabel();
        logoLabel.setPreferredSize(new Dimension(100, 110));
        logoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        File logoFile = new File("2.png");
        if (logoFile.exists()) {
            try {
                logoLabel.setIcon(createScaledKrsLogo(logoFile, 96, 96));
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Logo KRS tidak dapat dimuat: " + ex.getMessage(),
                        "Kesalahan Logo", JOptionPane.ERROR_MESSAGE);
                logoLabel.setText("Logo");
            }
        } else {
            logoLabel.setText("Logo");
            logoLabel.setFont(new Font("Serif", Font.BOLD, 11));
            logoLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        }

        JLabel judul = new JLabel("KARTU RENCANA STUDI", SwingConstants.CENTER);
        judul.setFont(new Font("Serif", Font.BOLD, 20));
        JLabel institusi = new JLabel("UNIVERSITAS HASYIM ASY'ARI", SwingConstants.CENTER);
        institusi.setFont(new Font("Serif", Font.BOLD, 14));
        JPanel heading = new JPanel(new GridLayout(2, 1, 0, 4));
        heading.setOpaque(false);
        heading.add(institusi);
        heading.add(judul);
        header.add(logoLabel, BorderLayout.WEST);
        header.add(heading, BorderLayout.CENTER);

        String foto = fotoMahasiswa.get(mahasiswa.getNim());
        JLabel fotoLabel = new JLabel();
        fotoLabel.setPreferredSize(new Dimension(90, 110));
        fotoLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        fotoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        if (foto != null && new File(foto).exists()) {
            ImageIcon icon = new ImageIcon(foto);
            fotoLabel.setIcon(new ImageIcon(icon.getImage().getScaledInstance(86, 106, Image.SCALE_SMOOTH)));
        } else {
            fotoLabel.setText("Foto");
        }
        header.add(fotoLabel, BorderLayout.EAST);
        dokumen.add(header, BorderLayout.NORTH);

        JPanel isi = new JPanel(new BorderLayout(0, 14));
        isi.setOpaque(false);
        JLabel identitas = new JLabel("NIM: " + mahasiswa.getNim() + "     Nama: " + mahasiswa.getNama());
        identitas.setFont(new Font("Serif", Font.PLAIN, 13));
        isi.add(identitas, BorderLayout.NORTH);
        String[] kolom = { "No", "Mata Kuliah", "Jam", "SKS" };
        DefaultTableModel model = new DefaultTableModel(kolom, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        ArrayList<KrsItem> items = getKrsTerpilih();
        int totalSks = 0;
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                KrsItem item = items.get(i);
                totalSks += item.sks;
                model.addRow(new Object[] { i + 1, item.mataKuliah, getJamKrs(item.mataKuliah), item.sks });
            }
        }
        JTable tabelDokumen = new JTable(model);
        tabelDokumen.setRowHeight(26);
        tabelDokumen.getTableHeader().setReorderingAllowed(false);
        tabelDokumen.getColumnModel().getColumn(0).setPreferredWidth(35);
        tabelDokumen.getColumnModel().getColumn(1).setPreferredWidth(245);
        tabelDokumen.getColumnModel().getColumn(2).setPreferredWidth(115);
        tabelDokumen.getColumnModel().getColumn(3).setPreferredWidth(45);
        isi.add(new JScrollPane(tabelDokumen), BorderLayout.CENTER);
        JLabel total = new JLabel(
                "Total SKS: " + totalSks + "     Semester: __________     Tahun Akademik: __________");
        total.setFont(new Font("Serif", Font.BOLD, 12));
        isi.add(total, BorderLayout.SOUTH);
        dokumen.add(isi, BorderLayout.CENTER);

        JPanel tandaTangan = new JPanel(new GridLayout(1, 2, 20, 0));
        tandaTangan.setOpaque(false);
        tandaTangan.add(new JLabel("Menyetujui, Dosen Wali", SwingConstants.CENTER));
        tandaTangan.add(new JLabel("Mahasiswa", SwingConstants.CENTER));
        tandaTangan.setBorder(new EmptyBorder(20, 0, 20, 0));
        dokumen.add(tandaTangan, BorderLayout.SOUTH);
        dokumen.setPreferredSize(new Dimension(540, 720));
        return dokumen;
    }

    private ImageIcon createScaledKrsLogo(File file, int maxWidth, int maxHeight) throws IOException {
        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            throw new IOException("Format file gambar tidak dikenali.");
        }

        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < minX || maxY < minY) {
            throw new IOException("Gambar logo tidak berisi piksel yang terlihat.");
        }

        BufferedImage cropped = image.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
        double scale = Math.min((double) maxWidth / cropped.getWidth(), (double) maxHeight / cropped.getHeight());
        int width = Math.max(1, (int) Math.round(cropped.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(cropped.getHeight() * scale));
        return new ImageIcon(cropped.getScaledInstance(width, height, Image.SCALE_SMOOTH));
    }

    private String getJamKrs(String mataKuliah) {
        for (JadwalKuliah jadwal : listJadwal) {
            String label = jadwal.mataKuliah + " (" + jadwal.sks + " SKS)";
            if (label.equals(mataKuliah) || jadwal.mataKuliah.equals(mataKuliah)) {
                return jadwal.jam;
            }
        }
        return "-";
    }

    private void previewKrs() {
        Mahasiswa mahasiswa = (Mahasiswa) cmbNimKrs.getSelectedItem();
        if (mahasiswa == null) {
            JOptionPane.showMessageDialog(this, "Pilih mahasiswa terlebih dahulu.", "Peringatan",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        JPanel dokumen = buatDokumenKrs(mahasiswa);
        JDialog dialog = new JDialog(this, "Preview KRS - " + mahasiswa.getNama(), true);
        JButton btnCetak = new JButton("Cetak ke Printer (A4)");
        btnCetak.addActionListener(e -> cetakKrs(dokumen));
        JPanel bawah = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bawah.add(btnCetak);
        dialog.add(new JScrollPane(dokumen), BorderLayout.CENTER);
        dialog.add(bawah, BorderLayout.SOUTH);
        dialog.setSize(650, 800);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void cetakKrs(JPanel dokumen) {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName("Cetak KRS Mahasiswa");
        Printable printable = new Printable() {
            @Override
            public int print(Graphics graphics, PageFormat format, int pageIndex) throws PrinterException {
                if (pageIndex > 0)
                    return NO_SUCH_PAGE;
                Graphics2D g2 = (Graphics2D) graphics.create();
                g2.translate(format.getImageableX(), format.getImageableY());
                double scale = Math.min(format.getImageableWidth() / dokumen.getPreferredSize().width,
                        format.getImageableHeight() / dokumen.getPreferredSize().height);
                g2.scale(scale, scale);
                dokumen.setSize(dokumen.getPreferredSize());
                dokumen.printAll(g2);
                g2.dispose();
                return PAGE_EXISTS;
            }
        };
        PageFormat format = job.defaultPage();
        Paper paper = new Paper();
        paper.setSize(595.28, 841.89);
        paper.setImageableArea(36, 36, 523.28, 769.89);
        format.setPaper(paper);
        job.setPrintable(printable, format);
        if (job.printDialog()) {
            try {
                job.print();
            } catch (PrinterException ex) {
                JOptionPane.showMessageDialog(this, "Gagal mencetak KRS: " + ex.getMessage(), "Kesalahan Cetak",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void hapusData() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Pilih baris pada tabel nilai yang ingin dihapus!", "Peringatan",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = table.convertRowIndexToModel(selectedRow);
        String nimDihapus = (String) tableModel.getValueAt(modelRow, 0);

        int confirm = JOptionPane.showConfirmDialog(this, "Apakah Anda yakin ingin menghapus data ini?", "Konfirmasi",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                storage.hapusMahasiswa(nimDihapus);
            } catch (Exception ex) {
                tampilkanErrorPenyimpanan(ex);
                return;
            }
            listMahasiswa.remove(modelRow);
            tableModel.removeRow(modelRow);
            dataKrs.remove(nimDihapus);
            fotoMahasiswa.remove(nimDihapus);

            // Hapus juga dari combo box KRS
            for (int i = 0; i < cmbNimKrs.getItemCount(); i++) {
                if (cmbNimKrs.getItemAt(i).getNim().equals(nimDihapus)) {
                    cmbNimKrs.removeItemAt(i);
                    break;
                }
            }
            tampilkanKrsTerpilih();

            updateStatistik();
        }
    }

    private void clearForm() {
        txtNim.setText("");
        txtNama.setText("");
        txtNilai.setText("");
        txtNim.requestFocus();
    }

    private void updateStatistik() {
        int total = listMahasiswa.size();
        if (total == 0) {
            lblTotalMhs.setText("0");
            lblRataRata.setText("0.00");
            lblLulus.setText("0%");
            return;
        }

        double totalNilai = 0;
        int jumlahLulus = 0;

        for (Mahasiswa m : listMahasiswa) {
            totalNilai += m.getNilai();
            if (m.getNilai() >= 60) {
                jumlahLulus++;
            }
        }

        double rataRata = totalNilai / total;
        double persenLulus = ((double) jumlahLulus / total) * 100;

        lblTotalMhs.setText(String.valueOf(total));
        lblRataRata.setText(String.format("%.2f", rataRata));
        lblLulus.setText(String.format("%.1f%%", persenLulus));
    }

    private void muatDataPenyimpanan() {
        try {
            listMahasiswa.clear();
            tableModel.setRowCount(0);
            cmbNimKrs.removeAllItems();
            dataKrs.clear();
            fotoMahasiswa.clear();

            for (Mahasiswa mahasiswa : storage.loadMahasiswa()) {
                listMahasiswa.add(mahasiswa);
                cmbNimKrs.addItem(mahasiswa);
                tableModel.addRow(new Object[] { mahasiswa.getNim(), mahasiswa.getNama(),
                        String.format("%.2f", mahasiswa.getNilai()), mahasiswa.getGrade(), mahasiswa.getStatus() });
            }
            dataKrs.putAll(storage.loadKrs());
            fotoMahasiswa.putAll(storage.loadFoto());
            updateStatistik();
            tampilkanKrsTerpilih();
        } catch (Exception ex) {
            tampilkanErrorPenyimpanan(ex);
        }
    }

    private void tampilkanErrorPenyimpanan(Exception ex) {
        JOptionPane.showMessageDialog(this, "Data gagal disimpan atau dimuat: " + ex.getMessage(),
                "Kesalahan Database", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            try {
                new Main().setVisible(true);
            } catch (IllegalStateException ex) {
                JOptionPane.showMessageDialog(null, ex.getMessage() + "\n" + ex.getCause().getMessage(),
                        "Kesalahan Database", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}