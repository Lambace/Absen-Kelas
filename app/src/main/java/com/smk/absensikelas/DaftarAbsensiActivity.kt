package com.smk.absensikelas

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView


class DaftarAbsensiActivity : androidx.appcompat.app.AppCompatActivity() {
    private lateinit var db: DatabaseHelper
    private var idKelas: Int = -1
    private var namaKelas: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_daftar_absensi)

        db = DatabaseHelper(this)
        idKelas = intent.getIntExtra("id_kelas", -1)
        namaKelas = intent.getStringExtra("nama_kelas") ?: ""

        findViewById<TextView>(R.id.tvTitleHeader).text = "$namaKelas : Attendance"

        val rv = findViewById<RecyclerView>(R.id.rvDaftarAbsensi)
        rv.layoutManager = LinearLayoutManager(this)

        loadData(rv)
    }

    private fun loadData(rv: RecyclerView) {
        val data = db.getRekapAbsensiPerTanggal(idKelas)

        // Sesuaikan pemanggilan dengan constructor yang benar
        rv.adapter = AbsensiTanggalAdapter(
            data,
            onEdit = { tanggal ->
                val intent = Intent(this, AbsensiActivity::class.java).apply {
                    putExtra("id_kelas", idKelas)
                    putExtra("nama_kelas", namaKelas)
                    putExtra("tanggal_edit", tanggal)
                    putExtra("is_edit_mode", true)
                }
                startActivity(intent)
            },
            onDelete = { tanggal ->
                AlertDialog.Builder(this)
                    .setTitle("Hapus Absensi")
                    .setMessage("Hapus data absensi tanggal $tanggal?")
                    .setPositiveButton("Hapus") { _, _ ->
                        db.deleteAbsensiByTanggal(idKelas, tanggal)
                        loadData(rv)
                        Toast.makeText(this, "Data dihapus", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            }
        )
    }
}