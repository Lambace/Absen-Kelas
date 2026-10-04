package com.smk.absensikelas

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.smk.absensikelas.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class InputNilaiActivity : AppCompatActivity() {

    private lateinit var spinnerKelas: Spinner
    private lateinit var btnPilihTanggal: View
    private lateinit var tvTanggalNilai: TextView
    private lateinit var etKeterangan: EditText
    private lateinit var rvNilai: RecyclerView
    private lateinit var btnSimpanNilai: MaterialButton

    private lateinit var db: DatabaseHelper
    private var selectedDate: String = ""
    private var idKelasTerpilih: Int = 0
    private var listSiswa = mutableListOf<SiswaModel>()
    private lateinit var adapter: NilaiAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_input_nilai)

        db = DatabaseHelper(this)

        spinnerKelas = findViewById<Spinner>(R.id.spinnerKelas)
        btnPilihTanggal = findViewById<View>(R.id.btnPilihTanggal)
        tvTanggalNilai = findViewById<TextView>(R.id.tvTanggalNilai)
        etKeterangan = findViewById<EditText>(R.id.etKeterangan)
        rvNilai = findViewById<RecyclerView>(R.id.rvNilai)
        btnSimpanNilai = findViewById<MaterialButton>(R.id.btnSimpanNilai)

        setupDatePicker()
        loadKelas()
        setupRecycler()

        btnSimpanNilai.setOnClickListener { simpanSemuaNilai() }
    }

    private fun setupDatePicker() {
        val cal = Calendar.getInstance()
        selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        tvTanggalNilai.text = selectedDate

        btnPilihTanggal.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                tvTanggalNilai.text = selectedDate
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
    }

    private fun loadKelas() {
        val kelasList = db.getAllKelas()
        val names = kelasList.map { it.second }
        val ids = kelasList.map { it.first }

        spinnerKelas.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        spinnerKelas.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                idKelasTerpilih = ids[pos]
                loadSiswa()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
        if (ids.isNotEmpty()) idKelasTerpilih = ids[0]
    }

    private fun setupRecycler() {
        adapter = NilaiAdapter(listSiswa)
        rvNilai.layoutManager = LinearLayoutManager(this)
        rvNilai.adapter = adapter
    }

    private fun loadSiswa() {
        listSiswa.clear()
        listSiswa.addAll(db.getSiswaByKelas(idKelasTerpilih))
        adapter.notifyDataSetChanged()
    }

    private fun simpanSemuaNilai() {
        val keterangan = etKeterangan.text.toString().trim()

        // WAJIB isi nama ujian, karena ini yang tampil di profil
        if (keterangan.isEmpty()) {
            etKeterangan.error = "Isi nama ujian (UH1/UTS/UAS)"
            Toast.makeText(this, "Nama ujian belum diisi", Toast.LENGTH_SHORT).show()
            return
        }

        // PERUBAHAN: mapel dan keterangan sama-sama ambil dari etKeterangan
        val mapel = keterangan

        var tersimpan = 0
        val dbWrite = db.writableDatabase

        dbWrite.beginTransaction()
        try {
            for (siswa in listSiswa) {
                val nilai = adapter.getNilaiUntukSiswa(siswa.id)

                if (nilai >= 0) {
                    // hapus nilai lama dengan mapel & tanggal yang sama
                    dbWrite.execSQL(
                        "DELETE FROM nilai WHERE id_siswa=? AND tanggal=? AND mapel=?",
                        arrayOf(siswa.id, selectedDate, mapel)
                    )
                    // kolom keterangan sekarang PASTI dari etKeterangan
                    db.insertNilai(siswa.id, mapel, nilai, selectedDate, keterangan)
                    tersimpan++
                }
            }
            dbWrite.setTransactionSuccessful()
        } finally {
            dbWrite.endTransaction()
        }

        Toast.makeText(this, "Nilai '$keterangan' tersimpan untuk $tersimpan siswa", Toast.LENGTH_SHORT).show()
        finish()
    }
}