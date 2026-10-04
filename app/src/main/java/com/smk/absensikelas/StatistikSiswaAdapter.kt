package com.smk.absensikelas

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import com.smk.absensikelas.R
import java.util.Locale

class StatistikSiswaAdapter(
    private var data: List<SiswaStatistikModel>,
    private val db: DatabaseHelper
) : RecyclerView.Adapter<StatistikSiswaAdapter.ViewHolder>() {

    private var dataFull: List<SiswaStatistikModel> = ArrayList(data)

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNama: TextView = view.findViewById(R.id.tvNamaSiswa)
        val tvPersentase: TextView = view.findViewById(R.id.tvPersentase)
        val tvNilai: TextView = view.findViewById(R.id.tvRataNilai)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_statistik_siswa, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = data[position]
        holder.tvNama.text = item.nama
        holder.tvPersentase.text = String.format(Locale.US, "Hadir: %.1f%%", item.persentaseHadir)
        holder.tvNilai.text = "Nilai: ${item.rataNilai.toInt()}"

        val color = if (item.persentaseHadir < 75f) Color.RED else "#4CAF50".toColorInt()
        holder.tvPersentase.setTextColor(color)

        holder.itemView.setOnClickListener {
            showDetailDialog(holder.itemView.context, item)
        }
    }

    private fun showDetailDialog(context: android.content.Context, item: SiswaStatistikModel) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_detail_statistik, null)
        val tableAbsensi = view.findViewById<TableLayout>(R.id.tableAbsensi)
        val tableNilai = view.findViewById<TableLayout>(R.id.tableNilai)

        // isi tabel seperti sebelumnya (kode Anda sudah benar)
        val c1 = db.readableDatabase.rawQuery(
            "SELECT tanggal, status FROM absensi WHERE id_siswa=? ORDER BY tanggal",
            arrayOf(item.id.toString())
        )
        while (c1.moveToNext()) {
            val row = TableRow(context)
            row.addView(TextView(context).apply { text = c1.getString(0); setPadding(16,8,16,8) })
            row.addView(TextView(context).apply {
                text = c1.getString(1); gravity = Gravity.CENTER
                if (text.contains("A", true)) setTextColor(Color.RED)
            })
            tableAbsensi.addView(row)
        }
        c1.close()

        val c2 = db.readableDatabase.rawQuery(
            "SELECT tanggal, nilai FROM nilai WHERE id_siswa=? ORDER BY tanggal",
            arrayOf(item.id.toString())
        )
        while (c2.moveToNext()) {
            val row = TableRow(context)
            row.addView(TextView(context).apply { text = c2.getString(0); setPadding(16,8,16,8) })
            row.addView(TextView(context).apply {
                text = c2.getInt(1).toString(); gravity = Gravity.CENTER
                if (text.toString().toInt() < 70) setTextColor(Color.RED)
            })
            tableNilai.addView(row)
        }
        c2.close()

        AlertDialog.Builder(context)
            .setTitle("Detail: ${item.nama}")
            .setView(view)
            .setPositiveButton("Tutup", null)
            .show()
    }

    override fun getItemCount() = data.size

    fun filter(text: String) {
        if (dataFull.isEmpty()) dataFull = ArrayList(data)
        val q = text.lowercase(Locale.ROOT)
        data = if (q.isEmpty()) dataFull else dataFull.filter { it.nama.lowercase(Locale.ROOT).contains(q) }
        notifyDataSetChanged()
    }
}