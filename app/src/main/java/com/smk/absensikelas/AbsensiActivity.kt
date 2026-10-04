package com.smk.absensikelas

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.smk.absensikelas.R
import java.util.Calendar
import java.util.Locale
import kotlin.collections.iterator

class AbsensiActivity : androidx.appcompat.app.AppCompatActivity() {

    private lateinit var rvAbsensi: RecyclerView
    private lateinit var spinnerKelas: Spinner
    private lateinit var layoutTanggal: LinearLayout
    private lateinit var tvTanggal: TextView
    private lateinit var btnSimpanAbsensi: MaterialButton
    private lateinit var db: DatabaseHelper

    private var idKelasTerpilih: Int = -1
    private var tanggalDipilih: String = ""
    private var isEditMode: Boolean = false

    private var listKelas: List<Pair<Int, String>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_absensi)

        db = DatabaseHelper(this)
        rvAbsensi = findViewById(R.id.rvAbsensi)
        spinnerKelas = findViewById(R.id.spinnerKelas)
        layoutTanggal = findViewById(R.id.layoutTanggal)
        tvTanggal = findViewById(R.id.tvTanggal)
        btnSimpanAbsensi = findViewById(R.id.btnSimpanAbsensi)

        // === PERBAIKAN: TERIMA DARI HISTORY ===
        val tglDariIntent = intent.getStringExtra("tanggal_edit")?: intent.getStringExtra("tanggal")
        val idKelasDariIntent = intent.getIntExtra("id_kelas", -1)

        if (tglDariIntent!= null && idKelasDariIntent!= -1) {
            // Dibuka dari History -> langsung mode edit tanggal itu
            isEditMode = true
            tanggalDipilih = tglDariIntent
            idKelasTerpilih = idKelasDariIntent
            tvTanggal.text = tanggalDipilih
            layoutTanggal.isEnabled = false
            layoutTanggal.alpha = 0.6f
            btnSimpanAbsensi.text = getString(R.string.btn_update_absensi)
        } else {
            // Mode normal
            isEditMode = intent.getBooleanExtra("is_edit_mode", false)
            val cal = Calendar.getInstance()
            tanggalDipilih = String.format(Locale.US, "%04d-%02d-%02d",
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
            tvTanggal.text = tanggalDipilih
            btnSimpanAbsensi.text = getString(R.string.btn_simpan_absensi)
        }

        setupSpinnerKelas()
        setupDatePicker()
        setupSimpanAbsensi()

        // Jika dari History, langsung muat
        if (idKelasTerpilih!= -1) {
            muatDataSiswa()
        }
    }

    private fun setupSpinnerKelas() {
        listKelas = db.getAllKelas()

        if (listKelas.isEmpty()) {
            Toast.makeText(this, "Belum ada data kelas. Tambah kelas dulu.", Toast.LENGTH_LONG).show()
            spinnerKelas.isEnabled = false
            return
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listKelas.map { it.second })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerKelas.adapter = adapter

        if (idKelasTerpilih!= -1) {
            val index = listKelas.indexOfFirst { it.first == idKelasTerpilih }
            if (index >= 0) spinnerKelas.setSelection(index)
            spinnerKelas.isEnabled =!isEditMode
        } else {
            idKelasTerpilih = listKelas.first().first
        }

        spinnerKelas.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (!isEditMode) {
                    idKelasTerpilih = listKelas[position].first
                    muatDataSiswa()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupDatePicker() {
        layoutTanggal.setOnClickListener {
            if (isEditMode) return@setOnClickListener

            val calendar = Calendar.getInstance()
            try {
                val parts = tanggalDipilih.split("-")
                if (parts.size == 3) {
                    calendar.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                }
            } catch (_: Exception) {}

            DatePickerDialog(this, { _, year, month, day ->
                tanggalDipilih = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
                tvTanggal.text = tanggalDipilih
                muatDataSiswa()
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }
    }

    private fun muatDataSiswa() {
        if (idKelasTerpilih == -1 || tanggalDipilih.isEmpty()) {
            Toast.makeText(this, getString(R.string.label_pilih_kelas), Toast.LENGTH_SHORT).show()
            rvAbsensi.adapter = null
            return
        }

        val dataSiswa = db.getSiswaByKelas(idKelasTerpilih)

        if (dataSiswa.isEmpty()) {
            Toast.makeText(this, "Tidak ada data siswa di kelas ini", Toast.LENGTH_SHORT).show()
            rvAbsensi.adapter = null
            return
        }

        val adapter = AbsensiAdapter(dataSiswa, db, tanggalDipilih)
        rvAbsensi.layoutManager = LinearLayoutManager(this)
        rvAbsensi.adapter = adapter

        // Isi default HADIR untuk yang belum ada
        dataSiswa.forEach { siswa ->
            val statusLama = try { db.getStatusAbsensi(siswa.id, tanggalDipilih) } catch (e: Exception) { null }
            adapter.statusMap[siswa.id] = statusLama?: "Hadir"
        }
        adapter.notifyDataSetChanged()
    }

    private fun setupSimpanAbsensi() {
        btnSimpanAbsensi.setOnClickListener {
            if (idKelasTerpilih == -1 || tanggalDipilih.isEmpty()) {
                Toast.makeText(this, getString(R.string.label_pilih_kelas), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val adapter = rvAbsensi.adapter as? AbsensiAdapter
            if (adapter == null) {
                Toast.makeText(this, "Tidak ada data absensi yang dimuat", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val dataSiswa = db.getSiswaByKelas(idKelasTerpilih)
            dataSiswa.forEach { siswa ->
                if (!adapter.statusMap.containsKey(siswa.id)) {
                    adapter.statusMap[siswa.id] = "Hadir"
                }
            }

            var jumlahTersimpan = 0
            for ((idSiswa, status) in adapter.statusMap) {
                db.updateAtauSimpanAbsensi(idSiswa, tanggalDipilih, status)
                jumlahTersimpan++
            }

            val pesan = if (isEditMode) "Update absensi berhasil ($jumlahTersimpan siswa)" else "Simpan absensi berhasil ($jumlahTersimpan siswa)"
            Toast.makeText(this, pesan, Toast.LENGTH_SHORT).show()

            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
    }
}