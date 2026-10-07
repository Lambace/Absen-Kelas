package com.smk.absensikelas

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class InputOrangTuaActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var spinnerKelas: Spinner
    private lateinit var spinnerSiswa: Spinner
    private lateinit var spinnerStatus: Spinner
    private lateinit var rvOrangTua: RecyclerView
    private lateinit var tvKosong: TextView

    private lateinit var etNama: EditText
    private lateinit var etNoHp: EditText

    private val listStatus = listOf("Ayah", "Ibu", "Wali")

    private var listKelas = listOf<Pair<Int, String>>()
    private var listSiswa = listOf<SiswaModel>()
    private var idKelasTerpilih: Int = -1
    private var idSiswaTerpilih: Int = -1
    private var tahunAjaran: String = ""
    private var semester: String = "Ganjil"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_input_orang_tua)

        db = DatabaseHelper(this)

        spinnerKelas = findViewById(R.id.spinnerKelasOT)
        spinnerSiswa = findViewById(R.id.spinnerSiswaOT)
        spinnerStatus = findViewById(R.id.spinnerStatusOT)
        rvOrangTua = findViewById(R.id.rvOrangTua)
        tvKosong = findViewById(R.id.tvKosongOT)

        etNama = findViewById(R.id.etNamaOrangTua)
        etNoHp = findViewById(R.id.etNoHpOrangTua)

        spinnerStatus.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listStatus)

        val tahunFull = intent.getStringExtra("tahun_ajaran")
            ?: getSharedPreferences("app_prefs", MODE_PRIVATE).getString("tahun_ajaran", "") ?: ""
        tahunAjaran = tahunFull.split(" - ").firstOrNull()?.trim() ?: ""
        semester = intent.getStringExtra("semester")
            ?: tahunFull.split(" - ").getOrElse(1) { "Ganjil" }.trim()

        setupRecyclerView()
        setupSpinnerKelas()

        findViewById<Button>(R.id.btnSimpanOrangTua).setOnClickListener { simpanData() }
        findViewById<Button>(R.id.btnHapusOrangTua).setOnClickListener { konfirmasiHapusSemua() }
    }

    override fun onResume() {
        super.onResume()
        muatDaftarOrangTua()
    }

    private fun setupRecyclerView() {
        rvOrangTua.layoutManager = LinearLayoutManager(this)
    }

    private fun muatDaftarOrangTua() {
        val data = db.getAllOrangTua()
        tvKosong.visibility = if (data.isEmpty()) View.VISIBLE else View.GONE
        rvOrangTua.adapter = OrangTuaAdapter(
            data = data,
            onEditClick = { d ->
                val idSiswa = d["id_siswa"]?.toIntOrNull() ?: return@OrangTuaAdapter
                val status = d["status"].orEmpty()
                // tampilkan di form untuk diedit
                etNama.setText(d["nama"].orEmpty())
                etNoHp.setText(d["no_hp"].orEmpty())
                val posStatus = listStatus.indexOf(status).takeIf { it >= 0 } ?: 0
                spinnerStatus.setSelection(posStatus)

                val siswa = listSiswa.find { it.id == idSiswa }
                if (siswa != null) {
                    spinnerSiswa.setSelection(listSiswa.indexOf(siswa))
                } else {
                    Toast.makeText(this, "Siswa berada di kelas lain, pilih kelas & siswa yang sesuai lalu simpan", Toast.LENGTH_SHORT).show()
                }
                // tandai baris yang sedang diedit
                (rvOrangTua.findViewHolderForAdapterPosition(data.indexOf(d)) as? OrangTuaAdapter.VH)?.setSelected(true)
            },
            onDeleteClick = { d ->
                val id = d["id"]?.toIntOrNull() ?: return@OrangTuaAdapter
                AlertDialog.Builder(this)
                    .setTitle("Hapus Data")
                    .setMessage("Hapus data ${d["status"]} dari ${d["nama_siswa"]}?")
                    .setPositiveButton("Hapus") { _, _ ->
                        if (db.deleteOrangTuaById(id) > 0) {
                            Toast.makeText(this, "Data dihapus", Toast.LENGTH_SHORT).show()
                            muatDaftarOrangTua()
                        }
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            }
        )
    }

    private fun setupSpinnerKelas() {
        val semuaLengkap = db.getAllKelasLengkap()
        val filtered = if (tahunAjaran.isNotEmpty()) {
            semuaLengkap.filter {
                it["nama_kelas"]?.contains(tahunAjaran) == true &&
                        (it["semester"] == semester || it["semester"].isNullOrEmpty())
            }
        } else semuaLengkap

        listKelas = filtered.map { it["id"]!!.toInt() to (it["nama_kelas"] ?: "") }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listKelas.map { it.second }.ifEmpty { listOf("-- Belum ada kelas --") })
        spinnerKelas.adapter = adapter

        spinnerKelas.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                idKelasTerpilih = listKelas.getOrNull(pos)?.first ?: -1
                if (idKelasTerpilih != -1) muatSpinnerSiswa()
                else {
                    listSiswa = emptyList()
                    idSiswaTerpilih = -1
                    spinnerSiswa.adapter = ArrayAdapter(this@InputOrangTuaActivity,
                        android.R.layout.simple_spinner_dropdown_item, listOf("-- Tidak ada siswa --"))
                }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun muatSpinnerSiswa() {
        listSiswa = db.getSiswaByKelas(idKelasTerpilih).sortedBy { it.nama }
        val namaList = listSiswa.map { if (it.nis.isNullOrBlank()) it.nama else "${it.nama} (${it.nis})" }
            .ifEmpty { listOf("-- Kelas ini belum punya siswa --") }
        spinnerSiswa.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, namaList)

        spinnerSiswa.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                idSiswaTerpilih = listSiswa.getOrNull(pos)?.id ?: -1
                if (idSiswaTerpilih != -1) muatFormOrangTua(idSiswaTerpilih)
                else kosongkanForm()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun muatFormOrangTua(idSiswa: Int) {
        // isi form dengan data orang tua pertama siswa ini (jika ada)
        val ot = db.getOrangTuaBySiswa(idSiswa).firstOrNull()
        if (ot == null) { kosongkanForm(); return }
        etNama.setText(ot.nama)
        etNoHp.setText(ot.noHp)
        val posStatus = listStatus.indexOf(ot.status).takeIf { it >= 0 } ?: 0
        spinnerStatus.setSelection(posStatus)
    }

    private fun kosongkanForm() {
        etNama.setText("")
        etNoHp.setText("")
        spinnerStatus.setSelection(0)
    }

    private fun simpanData() {
        if (idSiswaTerpilih == -1) {
            Toast.makeText(this, "Pilih kelas dan siswa dulu!", Toast.LENGTH_SHORT).show()
            return
        }
        val nama = etNama.text.toString().trim()
        val noHp = etNoHp.text.toString().trim()
        val status = spinnerStatus.selectedItem?.toString() ?: "Ayah"

        if (nama.isEmpty()) {
            Toast.makeText(this, "Isi nama orang tua/wali dulu!", Toast.LENGTH_SHORT).show()
            return
        }
        if (noHp.isEmpty()) {
            Toast.makeText(this, "Isi nomor HP dulu!", Toast.LENGTH_SHORT).show()
            return
        }

        // jika sudah ada data dengan status sama untuk siswa ini -> update, jika tidak -> insert
        val existing = db.getOrangTuaBySiswa(idSiswaTerpilih).find { it.status == status }
        val model = OrangTuaModel(
            id = existing?.id ?: 0,
            idSiswa = idSiswaTerpilih,
            nama = nama,
            noHp = noHp,
            status = status
        )
        val sukses = if (existing != null) {
            db.updateOrangTua(existing.id, model) > 0
        } else {
            db.insertOrangTua(model) != -1L
        }

        if (sukses) {
            Toast.makeText(this, "Data $status berhasil disimpan", Toast.LENGTH_SHORT).show()
            muatDaftarOrangTua()
        } else {
            Toast.makeText(this, "Gagal menyimpan data", Toast.LENGTH_SHORT).show()
        }
    }

    private fun konfirmasiHapusSemua() {
        if (idSiswaTerpilih == -1) {
            Toast.makeText(this, "Pilih siswa dulu", Toast.LENGTH_SHORT).show()
            return
        }
        val siswa = listSiswa.find { it.id == idSiswaTerpilih }
        val namaSiswa = siswa?.nama ?: "siswa ini"
        AlertDialog.Builder(this)
            .setTitle("Hapus Data Orang Tua")
            .setMessage("Hapus SEMUA data orang tua atas nama $namaSiswa?")
            .setPositiveButton("Hapus") { _, _ ->
                val n = db.deleteOrangTuaBySiswa(idSiswaTerpilih)
                if (n > 0) {
                    Toast.makeText(this, "$n data dihapus", Toast.LENGTH_SHORT).show()
                    kosongkanForm()
                    muatDaftarOrangTua()
                } else {
                    Toast.makeText(this, "Tidak ada data yang dihapus", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
