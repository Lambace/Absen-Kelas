package com.smk.absensikelas

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.utils.ColorTemplate
import com.smk.absensikelas.R
import java.util.Locale

class StatistikKelasActivity : androidx.appcompat.app.AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private var idKelas: Int = -1
    private var namaKelas: String = ""
    private lateinit var tvNamaKelas: TextView
    private var tipeDownload = ""

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == RESULT_OK) {
            res.data?.data?.let { uri ->
                if (tipeDownload == "NILAI") exportNilai(uri) else exportAbsen(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statistik_kelas)

        db = DatabaseHelper(this)
        idKelas = intent.getIntExtra("id_kelas", -1)
        namaKelas = intent.getStringExtra("nama_kelas") ?: ""

        // --- SOLUSI BARU: pakai ActionBar, bukan tvNamaKelas ---
        val tahunFull = getSharedPreferences("app_prefs", MODE_PRIVATE)
            .getString("tahun_ajaran", "2025/2026 - Ganjil") ?: "2025/2026 - Ganjil"

        val namaBersih = namaKelas.replace(Regex("\\s*-\\s*\\d{4}/\\d{4}.*"), "").trim()
        val judulLengkap = "$namaBersih - $tahunFull"

        supportActionBar?.title = judulLengkap
        supportActionBar?.subtitle = "Statistik Kelas"

        // tetap isi TextView lama biar tidak error, tapi kita tidak pedulikan
        tvNamaKelas = findViewById(R.id.tvNamaKelas)
        tvNamaKelas.text = judulLengkap

        findViewById<TextView>(R.id.tvJumlahSiswa).text = "Total: ${db.getTotalSiswaByKelas(idKelas)} Siswa"
        findViewById<TextView>(R.id.tvRataNilai).text = "Rata-rata: ${String.format(Locale.US, "%.1f", db.getAverageNilaiByKelas(idKelas))}"

        setupPie()
        setupBar()
        setupList()
        findViewById<Button>(R.id.btnDownloadRekap).setOnClickListener { pilihDownload() }
        findViewById<SearchView>(R.id.searchSiswa).setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(q: String?) = false
                override fun onQueryTextChange(t: String?): Boolean {
                    (findViewById<RecyclerView>(R.id.rvStatistik).adapter as? StatistikSiswaAdapter)?.filter(t ?: "")
                    return true
                }
            })
    }
    private fun setupPie() {
        findViewById<PieChart>(R.id.pieChartAbsensi)?.apply {
            val entries = db.getAbsensiEntries(idKelas)
            data = PieData(PieDataSet(entries, "").apply {
                colors = ColorTemplate.MATERIAL_COLORS.toList()
                valueTextSize = 12f
            })
            description.isEnabled = false
            legend.isEnabled = true
            animateY(800)
            invalidate()
        }
    }

    private fun setupBar() {
        findViewById<BarChart>(R.id.barChartNilai)?.apply {
            val (entries, labels) = db.getTop5NilaiSiswaLengkap(idKelas)
            data = BarData(BarDataSet(entries, "Nilai").apply {
                colors = ColorTemplate.JOYFUL_COLORS.toList()
                valueTextSize = 12f
            })
            description.isEnabled = false
            xAxis.valueFormatter = com.github.mikephil.charting.formatter.IndexAxisValueFormatter(labels)
            xAxis.granularity = 1f
            animateY(800)
            invalidate()
        }
    }

    private fun setupList() {
        val rv = findViewById<RecyclerView>(R.id.rvStatistik)
        rv.layoutManager = LinearLayoutManager(this)
        rv.isNestedScrollingEnabled = false
        rv.adapter = StatistikSiswaAdapter(db.getStatistikPerSiswa(idKelas), db)
    }

    private fun showEditDialog() { /* kode Anda yang lama, tetap pakai */ }
    private fun pilihDownload() { /* kode Anda yang lama */ }
    private fun exportAbsen(uri: Uri) { /* kode lama */ }
    private fun exportNilai(uri: Uri) { /* kode lama */ }
}