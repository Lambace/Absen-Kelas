package com.smk.absensikelas

import com.smk.absensikelas.R
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import java.util.*

class MainActivity : androidx.appcompat.app.AppCompatActivity() {
    private lateinit var db: DatabaseHelper
    private lateinit var tvNamaGuru: TextView
    private lateinit var ivFotoGuru: ImageView
    private var imageUri: Uri? = null
    private lateinit var ivPreviewDialog: ImageView
    private lateinit var rvKelas: RecyclerView
    private lateinit var searchView: SearchView
    private lateinit var spinnerTahunAjaran: Spinner

    private lateinit var prefs: SharedPreferences
    private var tahunAjaranTerpilih: String = ""

    private lateinit var layoutSearchResults: LinearLayout
    private lateinit var rvSearch: RecyclerView
    private lateinit var btnCloseSearch: MaterialButton
    private lateinit var searchAdapter: SearchAdapter

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                imageUri = it
                if (::ivPreviewDialog.isInitialized) ivPreviewDialog.setImageURI(it)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private val backupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> uri?.let { doBackup(it) } }

    private val restoreLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { doRestore(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = DatabaseHelper(this)
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        tvNamaGuru = findViewById(R.id.tvNamaGuru)
        ivFotoGuru = findViewById(R.id.ivFotoGuru)
        rvKelas = findViewById(R.id.rvKelas)
        searchView = findViewById(R.id.searchSiswa)
        spinnerTahunAjaran = findViewById(R.id.spinnerTahunAjaran)

        layoutSearchResults = findViewById(R.id.layoutSearchResults)
        rvSearch = findViewById(R.id.rvSearchResults)
        btnCloseSearch = findViewById(R.id.btnCloseSearch)
        rvSearch.layoutManager = LinearLayoutManager(this)
        searchAdapter = SearchAdapter()
        rvSearch.adapter = searchAdapter

        loadFotoGuru()
        setupTahunAjaran()
        loadDataGuru()
        loadDataKelas()
        setupSearch()
        setupBackupReset()

        findViewById<CardView>(R.id.cardAbsensiHarian).setOnClickListener {
            val parts = tahunAjaranTerpilih.split(" - ")
            startActivity(Intent(this, AbsensiActivity::class.java)
                .putExtra("tahun_ajaran", parts[0])
                .putExtra("semester", parts.getOrElse(1){"Ganjil"}))
        }
        findViewById<CardView>(R.id.cardInputGuru).setOnClickListener { showCustomInputDialog("Profil Guru") }
        findViewById<CardView>(R.id.cardInputKelas).setOnClickListener { showInputKelasDialog() }
        findViewById<CardView>(R.id.cardInputSiswa).setOnClickListener {
            val parts = tahunAjaranTerpilih.split(" - ")
            startActivity(Intent(this, InputSiswaActivity::class.java)
                .putExtra("tahun_ajaran", parts[0])
                .putExtra("semester", parts.getOrElse(1){"Ganjil"}))
        }
        findViewById<CardView>(R.id.cardInputNilai).setOnClickListener {
            val parts = tahunAjaranTerpilih.split(" - ")
            startActivity(Intent(this, InputNilaiActivity::class.java)
                .putExtra("tahun_ajaran", parts[0])
                .putExtra("semester", parts.getOrElse(1){"Ganjil"}))
        }
        findViewById<CardView>(R.id.cardInputOrangTua).setOnClickListener {
            val parts = tahunAjaranTerpilih.split(" - ")
            startActivity(Intent(this, InputOrangTuaActivity::class.java)
                .putExtra("tahun_ajaran", parts[0])
                .putExtra("semester", parts.getOrElse(1){"Ganjil"}))
        }
    }

    override fun onResume() {
        super.onResume()
        loadDataGuru()
        loadDataKelas()
    }

    private fun setupSearch() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?) = true
            override fun onQueryTextChange(t: String?): Boolean {
                if (t.isNullOrBlank() || t.length < 2) {
                    layoutSearchResults.visibility = View.GONE
                    return true
                }
                val hasil = db.searchGlobal(t)
                searchAdapter.submitList(hasil)
                layoutSearchResults.visibility = if (hasil.isEmpty()) View.GONE else View.VISIBLE
                return true
            }
        })
        btnCloseSearch.setOnClickListener {
            layoutSearchResults.visibility = View.GONE
            searchView.setQuery("", false)
            searchView.clearFocus()
        }
        searchView.setOnCloseListener { layoutSearchResults.visibility = View.GONE; false }
    }

    private fun setupBackupReset() {
        findViewById<MaterialButton>(R.id.btnResetDB).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset Database")
                .setMessage("Hapus SEMUA data?")
                .setPositiveButton("Hapus") { _, _ ->
                    db.close()
                    deleteDatabase("absensi.db")
                    db = DatabaseHelper(this)
                    Toast.makeText(this, "Database dihapus", Toast.LENGTH_SHORT).show()
                    recreate()
                }
                .setNegativeButton("Batal", null).show()
        }

        findViewById<MaterialButton>(R.id.btnBackup).setOnClickListener { backupDatabase() }

        findViewById<MaterialButton>(R.id.btnRestore).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Restore Database")
                .setMessage("Pilih file backup.db")
                .setPositiveButton("Pilih") { _, _ -> restoreLauncher.launch(arrayOf("application/octet-stream", "*/*")) }
                .setNegativeButton("Batal", null).show()
        }
    }

    // === BACKUP NAMA PENDEK ===
    private fun backupDatabase() {
        val safeTahun = tahunAjaranTerpilih.replace("/", "-").replace(" ", "_")
        backupLauncher.launch("absensi_$safeTahun.db")
    }

    private fun doBackup(uri: Uri) {
        try {
            db.close()
            val dbFile = getDatabasePath("absensi.db")
            contentResolver.openOutputStream(uri)?.use { out ->
                dbFile.inputStream().use { inp -> inp.copyTo(out) }
            }
            db = DatabaseHelper(this)
            Toast.makeText(this, "Backup $tahunAjaranTerpilih berhasil", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            db = DatabaseHelper(this)
            Toast.makeText(this, "Backup gagal: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun doRestore(uri: Uri) {
        try {
            db.close()
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            contentResolver.openInputStream(uri)?.use { input ->
                val dbFile = getDatabasePath("absensi.db")
                dbFile.outputStream().use { output -> input.copyTo(output) }
            }
            db = DatabaseHelper(this)
            Toast.makeText(this, "Restore berhasil! Restart...", Toast.LENGTH_LONG).show()
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            startActivity(Intent.makeRestartActivityTask(intent?.component))
            finishAffinity()
        } catch (e: Exception) {
            db = DatabaseHelper(this)
            Toast.makeText(this, "Restore gagal: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showCustomInputDialog(title: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_input, null)
        val tvJudul = dialogView.findViewById<TextView>(R.id.tvTitleDialog)
        val etNama = dialogView.findViewById<EditText>(R.id.etInputUtama)
        ivPreviewDialog = dialogView.findViewById(R.id.ivPreviewFoto)
        val btnPilih = dialogView.findViewById<Button>(R.id.btnPilihFoto)
        val btnSimpan = dialogView.findViewById<Button>(R.id.btnSimpanDialog)

        tvJudul.text = title
        etNama.setText(tvNamaGuru.text)
        db.getGuru()?.get("foto")?.let { uriStr ->
            if (uriStr.isNotEmpty()) try { imageUri = Uri.parse(uriStr); ivPreviewDialog.setImageURI(imageUri) } catch (_: Exception) {}
        }
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        btnPilih.setOnClickListener { pickImageLauncher.launch(arrayOf("image/*")) }
        btnSimpan.setOnClickListener {
            val nama = etNama.text.toString().trim()
            if (nama.isNotEmpty()) {
                saveDataGuru(nama)
                tvNamaGuru.text = nama
                loadDataGuru()
                dialog.dismiss()
                val mapel = prefs.getString("mapel_guru", "")?: ""
                if (mapel.isEmpty()) {
                    val etMapel = EditText(this).apply { hint = "Mata pelajaran"; setPadding(40,30,40,30) }
                    AlertDialog.Builder(this).setTitle("Mata Pelajaran").setView(etMapel)
                        .setPositiveButton("OK") { _, _ ->
                            val isi = etMapel.text.toString().trim()
                            if (isi.isNotEmpty()) { prefs.edit().putString("mapel_guru", isi).apply() }
                        }.setNegativeButton("Nanti", null).show()
                }
            }
        }
        dialog.show()
    }

    private fun loadFotoGuru() {
        val iv = findViewById<ShapeableImageView>(R.id.ivFotoGuru)
        val fotoStr = db.getGuru()?.get("foto").orEmpty()

        if (fotoStr.isEmpty()) {
            iv.setImageResource(R.mipmap.ic_launcher_round)
            return
        }

        try {
            val uri = Uri.parse(fotoStr)
            // coba buka dulu, kalau tidak ada izin langsung throw
            contentResolver.openInputStream(uri)?.use { input ->
                val bitmap = android.graphics.BitmapFactory.decodeStream(input)
                if (bitmap != null) {
                    iv.setImageBitmap(bitmap)
                    return
                }
            }
            throw Exception("Tidak bisa buka")
        } catch (_: Exception) {
            // URI rusak / izin hilang -> pakai default dan bersihkan DB
            iv.setImageResource(R.mipmap.ic_launcher_round)
            val namaGuru = db.getGuru()?.get("nama") ?: ""
            db.updateGuru(namaGuru, "") // hapus uri yang bermasalah
        }
    }

    private fun setupTahunAjaran() {
        val cal = Calendar.getInstance()
        val tahunSekarang = cal.get(Calendar.YEAR)
        val bulan = cal.get(Calendar.MONTH)
        val awalBerjalan = if (bulan >= Calendar.JULY) tahunSekarang else tahunSekarang - 1
        val tahunList = mutableListOf<String>()
        for (awal in awalBerjalan-4..awalBerjalan+1) {
            val tahun = "$awal/${awal+1}"
            tahunList.add("$tahun - Ganjil")
            tahunList.add("$tahun - Genap")
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, tahunList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTahunAjaran.adapter = adapter
        val default = "$awalBerjalan/${awalBerjalan+1} - Ganjil"
        tahunAjaranTerpilih = prefs.getString("tahun_ajaran", default)!!
        if (tahunAjaranTerpilih!in tahunList) { tahunList.add(0, tahunAjaranTerpilih); adapter.notifyDataSetChanged() }
        spinnerTahunAjaran.setSelection(tahunList.indexOf(tahunAjaranTerpilih).coerceAtLeast(0))
        spinnerTahunAjaran.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                tahunAjaranTerpilih = tahunList[pos]
                prefs.edit().putString("tahun_ajaran", tahunAjaranTerpilih).apply()
                loadDataKelas()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun showInputKelasDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_input_kelas, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        val etNama = view.findViewById<EditText>(R.id.etNamaKelas)
        val etJurusan = view.findViewById<EditText>(R.id.etJurusan)
        val etWali = view.findViewById<EditText>(R.id.etWaliKelas)
        val etWa = view.findViewById<EditText>(R.id.etNomorWaWali)
        view.findViewById<Button>(R.id.btnSimpanKelas).setOnClickListener {
            val namaDasar = etNama.text.toString().trim().replace(Regex("\\s*-\\s*\\d{4}/\\d{4}.*"), "")
            if (namaDasar.isNotEmpty()) {
                val parts = tahunAjaranTerpilih.split(" - ")
                val tahun = parts[0]
                val semester = parts.getOrElse(1) { "Ganjil" }
                val namaFull = "$namaDasar - $tahun - $semester"
                db.insertKelas(namaFull, etJurusan.text.toString(), etWali.text.toString(), etWa.text.toString(), semester)
                loadDataKelas()
                dialog.dismiss()
            }
        }
        dialog.show()
    }
    private fun saveDataGuru(nama: String) { db.updateGuru(nama, imageUri?.toString()?: "") }

    private fun loadDataGuru() {
        db.getGuru()?.let { data ->
            tvNamaGuru.text = data["nama"]
            data["foto"]?.takeIf { it.isNotEmpty() }?.let { try { ivFotoGuru.setImageURI(Uri.parse(it)) } catch (_: Exception) {} }
        }
    }

    private fun loadDataKelas() {

        val parts = tahunAjaranTerpilih.split(" - ")
        val tahun = parts[0]
        val semester = parts.getOrElse(1) { "Ganjil" }
        val semua = db.getAllKelasLengkap()
        val filtered = semua.filter { it["nama_kelas"]?.contains("$tahun - $semester") == true }

        rvKelas.layoutManager = LinearLayoutManager(this)
        rvKelas.adapter = KelasAdapter(
            listKelas = filtered,
            onItemClick = { id, namaTampil ->
                startActivity(Intent(this, DetailKelasActivity::class.java).apply {
                    putExtra("id_kelas", id)
                    putExtra("nama_kelas", namaTampil)
                })
            },
            onEditClick = { idKelas ->
                showEditKelasDialog(idKelas)
            },
            onDeleteClick = { idKelas, namaTampil ->
                AlertDialog.Builder(this)
                    .setTitle("Hapus Kelas")
                    .setMessage("Hapus $namaTampil?")
                    .setPositiveButton("Hapus") { _, _ ->
                        db.deleteKelas(idKelas)
                        loadDataKelas()
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            }
        )
    }

    private fun showEditKelasDialog(idKelas: Int) {
        val data = db.getKelasById(idKelas)?: return
        val v = layoutInflater.inflate(R.layout.dialog_input_kelas, null)
        val etNama = v.findViewById<EditText>(R.id.etNamaKelas)
        val etJur = v.findViewById<EditText>(R.id.etJurusan)
        val etWali = v.findViewById<EditText>(R.id.etWaliKelas)
        val etWa = v.findViewById<EditText>(R.id.etNomorWaWali)
        val namaOnly = (data["nama"]?: "").replace(Regex("\\s*-\\s*\\d{4}/\\d{4}.*"), "").trim()
        etNama.setText(namaOnly); etJur.setText(data["jurusan"]); etWali.setText(data["wali"]); etWa.setText(data["wa"])
        AlertDialog.Builder(this).setTitle("Edit Kelas").setView(v)
            .setPositiveButton("Update") { _, _ ->
                val clean = etNama.text.toString().trim().replace(Regex("\\s*-\\s*\\d{4}/\\d{4}.*"), "")
                val parts = tahunAjaranTerpilih.split(" - ")
                val tahun = parts[0]
                val semester = parts.getOrElse(1) { "Ganjil" }
                val namaFull = "$clean - $tahun - $semester"
                db.updateKelas(idKelas, namaFull, etJur.text.toString(), etWali.text.toString(), etWa.text.toString(), semester)
                loadDataKelas()
            }.setNegativeButton("Batal", null).show()
    }

    inner class SearchAdapter : RecyclerView.Adapter<SearchAdapter.VH>() {
        private var data = listOf<DatabaseHelper.HasilCari>()
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val nama: TextView = v.findViewById(R.id.tvNama)
            val detail: TextView = v.findViewById(R.id.tvDetail)
            val persen: TextView = v.findViewById(R.id.tvPersen)
            val rata: TextView = v.findViewById(R.id.tvRata)
        }
        fun submitList(l: List<DatabaseHelper.HasilCari>) { data = l; notifyDataSetChanged() }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(layoutInflater.inflate(R.layout.item_search_result, p, false))
        override fun getItemCount() = data.size
        override fun onBindViewHolder(h: VH, i: Int) {
            val s = data[i]
            h.nama.text = "[${s.tipe}] ${s.nama}"
            h.detail.text = s.detail
            h.persen.text = "Hadir ${"%.1f".format(s.persenHadir)}%"
            h.rata.text = "Nilai ${"%.1f".format(s.rataNilai)}"
        }
    }
}