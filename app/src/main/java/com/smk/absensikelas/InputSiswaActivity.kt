package com.smk.absensikelas

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URLEncoder

class InputSiswaActivity : AppCompatActivity() {
    private lateinit var db: DatabaseHelper
    private lateinit var spinnerKelas: Spinner
    private lateinit var rvSiswa: RecyclerView
    private var listKelas = listOf<Pair<Int, String>>()
    private var idKelasTerpilih: Int = -1
    private var tahunAjaran: String = ""
    private var semester: String = "Ganjil"
    private lateinit var adapterSiswa: SiswaAdapter

    private val pencariFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            prosesImportCSV(it)
        }
    }

    private val createFormatLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    contentResolver.openOutputStream(uri)?.use { os ->
                        os.writer().use {
                            it.append("nama_siswa,nis,jenis_kelamin\n")
                            it.append("Contoh Nama,,Laki-laki\n") // NIS boleh kosong
                        }
                    }
                    Toast.makeText(this, "Format CSV berhasil disimpan", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_input_siswa)

        db = DatabaseHelper(this)
        spinnerKelas = findViewById(R.id.spinnerKelasSiswa)
        rvSiswa = findViewById(R.id.rvDaftarSiswa)

        val tahunFull = intent.getStringExtra("tahun_ajaran")
            ?: getSharedPreferences("app_prefs", MODE_PRIVATE).getString("tahun_ajaran", "")?: ""
        tahunAjaran = tahunFull.split(" - ").firstOrNull()?.trim()?: ""
        semester = intent.getStringExtra("semester")
            ?: tahunFull.split(" - ").getOrElse(1) { "Ganjil" }.trim()

        setupAdapter()
        setupSpinner()

        findViewById<Button>(R.id.btnTambahSiswaManual).setOnClickListener {
            if (idKelasTerpilih!= -1) dialogTambahManual()
            else Toast.makeText(this, "Pilih kelas dulu", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnMenuImport).setOnClickListener {
            if (idKelasTerpilih!= -1) {
                Toast.makeText(this, "Pilih file CSV dari Download", Toast.LENGTH_SHORT).show()
                pencariFileLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/vnd.ms-excel"))
            } else Toast.makeText(this, "Pilih kelas dulu!", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnUnduhFormat).setOnClickListener { unduhFormatCSV() }
    }

    private fun setupAdapter() {
        adapterSiswa = SiswaAdapter(
            onEditClick = { siswa: SiswaModel -> showEditDialog(siswa) },
            onDeleteClick = { siswa: SiswaModel -> konfirmasiHapus(siswa) },
            onWaClick = { siswa: SiswaModel ->
                val telp = db.getTelpWaliByKelas(idKelasTerpilih)
                if (telp.isNotEmpty()) kirimNotifikasiWhatsApp(siswa, telp)
                else Toast.makeText(this, "Nomor WA wali belum diisi", Toast.LENGTH_SHORT).show()
            },
            enableAlert = false,
            onItemClick = { siswa: SiswaModel ->
                val intent = Intent(this, ProfilSiswaActivity::class.java).apply {
                    putExtra("id_siswa", siswa.id)
                    putExtra("nama_siswa", siswa.nama)
                    putExtra("nis_siswa", siswa.nis?: "-")
                    putExtra("jk_siswa", siswa.jenisKelamin?: "-")
                    val namaKelas = listKelas.find { it.first == idKelasTerpilih }?.second?: ""
                    putExtra("nama_kelas", namaKelas)
                }
                startActivity(intent)
            }
        )
        rvSiswa.layoutManager = LinearLayoutManager(this)
        rvSiswa.adapter = adapterSiswa
    }

    private fun setupSpinner() {
        val semuaLengkap = db.getAllKelasLengkap()
        val filtered = if (tahunAjaran.isNotEmpty()) {
            semuaLengkap.filter {
                it["nama_kelas"]?.contains(tahunAjaran) == true &&
                        (it["semester"] == semester || it["semester"].isNullOrEmpty())
            }
        } else semuaLengkap

        listKelas = filtered.map {
            val namaDb = it["nama_kelas"]?: ""
            val display = if (namaDb.contains(Regex("\\d{4}/\\d{4}"))) {
                namaDb
            } else {
                val base = namaDb.replace(Regex("\\s*-\\s*\\d{4}/\\d{4}.*"), "").trim()
                "$base - $tahunAjaran - $semester"
            }
            Pair(it["id"]!!.toInt(), display)
        }

        if (listKelas.isEmpty()) {
            Toast.makeText(this, "Belum ada kelas untuk $tahunAjaran - $semester", Toast.LENGTH_LONG).show()
            return
        }
        spinnerKelas.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listKelas.map { it.second }).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerKelas.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                idKelasTerpilih = listKelas[pos].first
                muatDataSiswa()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    // === PERUBAHAN 1: NIS OPSIONAL (simpan NULL) ===
    private fun dialogTambahManual() {
        val v = layoutInflater.inflate(R.layout.dialog_input_siswa_manual, null)
        val etNama = v.findViewById<EditText>(R.id.etNamaSiswaManual)
        val etNis = v.findViewById<EditText>(R.id.etNisSiswaManual)
        val spJk = v.findViewById<Spinner>(R.id.spinnerJenisKelamin)
        spJk.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf("Laki-laki", "Perempuan"))

        AlertDialog.Builder(this).setTitle("Tambah Siswa").setView(v)
            .setPositiveButton("Simpan") { _, _ ->
                val nama = etNama.text.toString().trim()
                val nisInput = etNis.text.toString().trim()
                val nis: String? = nisInput.ifEmpty { null } // <-- NULL jika kosong
                val jk = spJk.selectedItem.toString()

                if (nama.isEmpty()) {
                    Toast.makeText(this, "Nama wajib diisi", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (nis!= null && db.isNisExists(nis)) {
                    Toast.makeText(this, "NIS sudah ada", Toast.LENGTH_SHORT).show()
                } else {
                    db.insertSiswa(idKelasTerpilih, nama, nis, jk)
                    muatDataSiswa()
                }
            }.setNegativeButton("Batal", null).show()
    }

    private fun muatDataSiswa() {
        if (idKelasTerpilih == -1) return
        val list = db.getSiswaByKelas(idKelasTerpilih).map { siswa ->
            val rekap = db.getRekapAlphaBolos(siswa.id)
            siswa.copy(alpha = rekap.first, bolos = rekap.second)
        }
        adapterSiswa.submitList(list)
    }

    private fun konfirmasiHapus(siswa: SiswaModel) {
        AlertDialog.Builder(this).setTitle("Hapus Siswa").setMessage("Hapus ${siswa.nama}?")
            .setPositiveButton("Hapus") { _, _ ->
                db.deleteSiswa(siswa.id)
                muatDataSiswa()
            }.setNegativeButton("Batal", null).show()
    }

    // === PERUBAHAN 2: EDIT JUGA OPSIONAL ===
    private fun showEditDialog(siswa: SiswaModel) {
        val v = layoutInflater.inflate(R.layout.dialog_edit_siswa, null)
        val etNama = v.findViewById<EditText>(R.id.etEditNama)
        val etNis = v.findViewById<EditText>(R.id.etEditNis)
        val rbL = v.findViewById<RadioButton>(R.id.rbEditL)
        val rbP = v.findViewById<RadioButton>(R.id.rbEditP)

        etNama.setText(siswa.nama)
        etNis.setText(siswa.nis?: "") // tampilkan kosong jika NULL
        if (siswa.jenisKelamin?.startsWith("L") == true) rbL.isChecked = true else rbP.isChecked = true

        AlertDialog.Builder(this).setTitle("Edit Siswa").setView(v)
            .setPositiveButton("Simpan") { _, _ ->
                val namaBaru = etNama.text.toString().trim()
                val nisInput = etNis.text.toString().trim()
                val nisBaru: String? = nisInput.ifEmpty { null }
                val jkBaru = if (rbL.isChecked) "Laki-laki" else "Perempuan"

                if (nisBaru!= null && nisBaru!= siswa.nis && db.isNisExists(nisBaru)) {
                    Toast.makeText(this, "NIS sudah dipakai", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                db.updateSiswa(siswa.id, namaBaru, nisBaru, jkBaru)
                muatDataSiswa()
                Toast.makeText(this, "Diperbarui", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Batal", null).show()
    }

    // === PERUBAHAN 3: IMPORT CSV ===
    private fun prosesImportCSV(uri: Uri) {
        var sukses = 0; var duplikat = 0
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input)).use { r ->
                    r.readLine()
                    var line: String?
                    while (r.readLine().also { line = it }!= null) {
                        val kolom = line!!.split(',')
                        if (kolom.size >= 2) {
                            val nama = kolom[0].trim()
                            val nisInput = kolom.getOrNull(1)?.trim()?: ""
                            val nis: String? = nisInput.ifEmpty { null }
                            val jk = kolom.getOrNull(2)?.trim()?.let { if (it.startsWith("P", true)) "Perempuan" else "Laki-laki" }?: "Laki-laki"

                            if (nama.isNotEmpty() && (nis == null ||!db.isNisExists(nis))) {
                                db.insertSiswa(idKelasTerpilih, nama, nis, jk)
                                sukses++
                            } else duplikat++
                        }
                    }
                }
            }
            muatDataSiswa()
            Toast.makeText(this, "Import: $sukses berhasil, $duplikat dilewati", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun unduhFormatCSV() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, "format_siswa.csv")
        }
        createFormatLauncher.launch(intent)
    }

    private fun kirimNotifikasiWhatsApp(siswa: SiswaModel, telpWali: String) {
        val rekap = db.getRekapAlphaBolos(siswa.id)
        val namaGuru = db.getGuru()?.get("nama")?: "Agussalim Tajuddin"
        val mapel = getSharedPreferences("app_prefs", MODE_PRIVATE)
            .getString("mapel_guru", "Matematika")?: "Matematika"
        val kelas = listKelas.find { it.first == idKelasTerpilih }?.second?: ""
        val tahunAjaranFull = "$tahunAjaran - $semester"

        val pesan = "Semangat Pagi Bapak/Ibu Wali Kelas hebat. Saya, $namaGuru guru $mapel " +
                "Menginformasikan bahwa ananda, ${siswa.nama} ( $kelas - $tahunAjaranFull ) " +
                "Tercatat Telah Alpha ${rekap.first}x dan Bolos ${rekap.second}x. " +
                "Mohon agar siswa tersebut diberi perhatian. Dikirim Otomatis By Sistem ABSENSI SISWA !!"

        try {
            val nomor = telpWali.replace(Regex("[^0-9]"), "").let {
                if (it.startsWith("0")) "62${it.substring(1)}" else it
            }
            val url = "https://wa.me/$nomor?text=${URLEncoder.encode(pesan, "UTF-8")}"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(this, "WhatsApp tidak terinstal", Toast.LENGTH_SHORT).show()
        }
    }
}