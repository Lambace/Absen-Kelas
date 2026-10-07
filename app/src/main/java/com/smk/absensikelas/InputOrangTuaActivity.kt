package com.smk.absensikelas

import android.os.Bundle
import android.view.View
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
    private lateinit var spinnerStatus: Spinner
    private lateinit var rvOrangTua: RecyclerView
    private lateinit var tvKosong: TextView

    private lateinit var etNama: EditText
    private lateinit var etNoHp: EditText

    private val listStatus = listOf("Ayah", "Ibu", "Wali")
    private var editId: Int = -1 // Untuk melacak jika sedang mengedit data

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_input_orang_tua)

        db = DatabaseHelper(this)

        spinnerStatus = findViewById(R.id.spinnerStatusOT)
        rvOrangTua = findViewById(R.id.rvOrangTua)
        tvKosong = findViewById(R.id.tvKosongOT)
        etNama = findViewById(R.id.etNamaOrangTua)
        etNoHp = findViewById(R.id.etNoHpOrangTua)

        spinnerStatus.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listStatus)
        
        setupRecyclerView()
        findViewById<Button>(R.id.btnSimpanOrangTua).setOnClickListener { simpanData() }
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
                editId = d["id"]?.toIntOrNull() ?: -1
                etNama.setText(d["nama"].orEmpty())
                etNoHp.setText(d["no_hp"].orEmpty())
                val posStatus = listStatus.indexOf(d["status"]).takeIf { it >= 0 } ?: 0
                spinnerStatus.setSelection(posStatus)
            },
            onDeleteClick = { d ->
                val id = d["id"]?.toIntOrNull() ?: return@OrangTuaAdapter
                AlertDialog.Builder(this)
                    .setTitle("Hapus Data")
                    .setMessage("Hapus data ${d["status"]} atas nama ${d["nama"]}?")
                    .setPositiveButton("Hapus") { _, _ ->
                        if (db.deleteOrangTuaById(id) > 0) {
                            Toast.makeText(this, "Data dihapus", Toast.LENGTH_SHORT).show()
                            muatDaftarOrangTua()
                            kosongkanForm()
                        }
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            }
        )
    }

    private fun kosongkanForm() {
        etNama.setText("")
        etNoHp.setText("")
        spinnerStatus.setSelection(0)
        editId = -1
    }

    private fun simpanData() {
        val nama = etNama.text.toString().trim()
        val noHp = etNoHp.text.toString().trim()
        val status = spinnerStatus.selectedItem?.toString() ?: "Ayah"

        if (nama.isEmpty() || noHp.isEmpty()) {
            Toast.makeText(this, "Nama dan Nomor WhatsApp wajib diisi!", Toast.LENGTH_SHORT).show()
            return
        }

        // id_siswa kita set 0 karena sekarang bersifat global/umum tidak terikat spesifik ke siswa
        val model = OrangTuaModel(
            id = editId,
            idSiswa = 0, 
            nama = nama,
            noHp = noHp,
            status = status
        )

        val sukses = if (editId != -1) {
            db.updateOrangTua(editId, model) > 0
        } else {
            db.insertOrangTua(model) != -1L
        }

        if (sukses) {
            Toast.makeText(this, "Data $status berhasil disimpan", Toast.LENGTH_SHORT).show()
            kosongkanForm()
            muatDaftarOrangTua()
        } else {
            Toast.makeText(this, "Gagal menyimpan data", Toast.LENGTH_SHORT).show()
        }
    }
}
