package com.smk.absensikelas

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.smk.absensikelas.R

class NilaiAdapter(private val listSiswa: List<SiswaModel>) :
    RecyclerView.Adapter<NilaiAdapter.ViewHolder>() {

    // Simpan nilai: ID Siswa -> Nilai (HANYA SATU deklarasi)
    private val nilaiMap = mutableMapOf<Int, Int>()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvInitial: TextView = view.findViewById(R.id.tvInitial)
        val tvNama: TextView = view.findViewById(R.id.tvNama)
        val tvNis: TextView = view.findViewById(R.id.tvNis)
        val etNilai: EditText = view.findViewById(R.id.etNilai)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_nilai, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val siswa = listSiswa[position]

        // 1. Data siswa
        holder.tvInitial.text = siswa.nama.firstOrNull()?.uppercase()?: "?"
        holder.tvNama.text = siswa.nama
        holder.tvNis.text = "NIS: ${siswa.nis?: "-"}"

        // 2. Hapus watcher lama (penting agar tidak dobel saat scroll)
        (holder.etNilai.tag as? TextWatcher)?.let { holder.etNilai.removeTextChangedListener(it) }

        // 3. Set nilai yang sudah ada
        val nilaiSekarang = nilaiMap[siswa.id]
        holder.etNilai.setText(if (nilaiSekarang!= null && nilaiSekarang > 0) nilaiSekarang.toString() else "")
        setWarnaNilai(holder.etNilai, nilaiSekarang)

        // 4. Watcher baru
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val nilai = s.toString().toIntOrNull()
                if (nilai!= null) {
                    nilaiMap[siswa.id] = nilai
                } else {
                    nilaiMap.remove(siswa.id)
                }
                setWarnaNilai(holder.etNilai, nilai)
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        holder.etNilai.addTextChangedListener(watcher)
        holder.etNilai.tag = watcher
    }

    private fun setWarnaNilai(et: EditText, nilai: Int?) {
        val warna = when {
            nilai == null -> 0xFF263238.toInt() // abu default
            nilai < 75 -> 0xFFD32F2F.toInt() // merah
            nilai < 85 -> 0xFFFF9800.toInt() // oranye
            else -> 0xFF388E3C.toInt() // hijau
        }
        et.setTextColor(warna)
    }

    override fun getItemCount(): Int = listSiswa.size

    // FUNGSI YANG DIPANGGIL InputNilaiActivity
    fun getNilaiUntukSiswa(idSiswa: Int): Int {
        return nilaiMap[idSiswa]?: 0
    }
}