package com.smk.absensikelas

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log // FIX: tambah log
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "absensi.db", null, 16) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE guru (id INTEGER PRIMARY KEY AUTOINCREMENT, nama TEXT, foto TEXT)")
        db.execSQL("""
            CREATE TABLE orang_tua (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                id_siswa INTEGER,
                nama TEXT NOT NULL,
                no_hp TEXT,
                status TEXT DEFAULT 'Ayah'
            )
        """)
        db.execSQL("CREATE TABLE kelas (id INTEGER PRIMARY KEY AUTOINCREMENT, nama_kelas TEXT, jurusan TEXT, wali_kelas TEXT, nomor_wa_wali TEXT, semester TEXT DEFAULT 'Ganjil')")
        // PERBAIKAN: hapus UNIQUE supaya NIS boleh kosong/duplikat
        db.execSQL("CREATE TABLE siswa (id INTEGER PRIMARY KEY AUTOINCREMENT, id_kelas INTEGER, nama_siswa TEXT, nis TEXT, jenis_kelamin TEXT)")
        db.execSQL("CREATE TABLE absensi (id INTEGER PRIMARY KEY AUTOINCREMENT, id_siswa INTEGER, tanggal TEXT, status TEXT, UNIQUE(id_siswa, tanggal) ON CONFLICT REPLACE)")
        db.execSQL("CREATE TABLE nilai (id INTEGER PRIMARY KEY AUTOINCREMENT, id_siswa INTEGER, mapel TEXT, nilai INTEGER, tanggal TEXT, keterangan TEXT)")
        db.execSQL("""
        CREATE TABLE IF NOT EXISTS log_notifikasi (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            id_siswa INTEGER,
            bulan INTEGER,
            tahun INTEGER,
            alpha INTEGER,
            bolos INTEGER,
            tanggal_kirim TEXT,
            UNIQUE(id_siswa, bulan, tahun)
        )
    """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v12: tambah kolom semester
        if (oldVersion < 12) {
            try {
                db.execSQL("ALTER TABLE kelas ADD COLUMN semester TEXT DEFAULT 'Ganjil'")
                Log.d("DB_UPGRADE", "Kolom semester ditambahkan")
            } catch (e: Exception) {
                Log.e("DB_UPGRADE", "Gagal tambah semester", e)
            }
        }

        // v13: tambah kolom keterangan di nilai
        if (oldVersion < 13) {
            try {
                val cursor = db.rawQuery("PRAGMA table_info(nilai)", null)
                var hasKeterangan = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "keterangan") {
                        hasKeterangan = true
                        break
                    }
                }
                cursor.close()
                if (!hasKeterangan) {
                    db.execSQL("ALTER TABLE nilai ADD COLUMN keterangan TEXT DEFAULT ''")
                    Log.d("DB_UPGRADE", "Kolom keterangan ditambahkan")
                }
            } catch (e: Exception) {
                Log.e("DB_UPGRADE", "Gagal tambah keterangan", e)
            }
        }

        // v14: HAPUS UNIQUE di nis (ini yang bikin import gagal)
        if (oldVersion < 14) {
            try {
                db.beginTransaction()
                db.execSQL("DROP TABLE IF EXISTS siswa_new")
                db.execSQL("CREATE TABLE siswa_new (id INTEGER PRIMARY KEY AUTOINCREMENT, id_kelas INTEGER, nama_siswa TEXT, nis TEXT, jenis_kelamin TEXT)")
                // PAKAI DISTINCT dan abaikan duplikat NIS yang bikin crash
                db.execSQL("INSERT OR IGNORE INTO siswa_new (id, id_kelas, nama_siswa, nis, jenis_kelamin) SELECT id, id_kelas, nama_siswa, nis, jenis_kelamin FROM siswa GROUP BY id")
                db.execSQL("DROP TABLE siswa")
                db.execSQL("ALTER TABLE siswa_new RENAME TO siswa")
                db.setTransactionSuccessful()
                Log.d("DB_UPGRADE", "Migrasi v14 sukses")
            } catch (e: Exception) {
                Log.e("DB_UPGRADE", "Migrasi gagal", e)
            } finally {
                db.endTransaction()
            }
        }

        // v15: tabel baru untuk data orang tua/wali siswa
        if (oldVersion < 15) {
            try {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS orang_tua (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        id_siswa INTEGER,
                        nama TEXT NOT NULL,
                        no_hp TEXT,
                        status TEXT DEFAULT 'Ayah'
                    )
                """)
                Log.d("DB_UPGRADE", "Migrasi v15 sukses: tabel orang_tua dibuat")
            } catch (e: Exception) {
                Log.e("DB_UPGRADE", "Gagal buat tabel orang_tua", e)
            }
        }

        // v16: sederhanakan data orang tua -> cukup nama, no HP, dan status (Ayah/Ibu/Wali)
        if (oldVersion < 16) {
            try {
                db.beginTransaction()
                db.execSQL("DROP TABLE IF EXISTS orang_tua_new")
                db.execSQL("""
                    CREATE TABLE orang_tua_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        id_siswa INTEGER,
                        nama TEXT NOT NULL,
                        no_hp TEXT,
                        status TEXT DEFAULT 'Ayah'
                    )
                """)
                // migrasi data lama (jika ada): pecah kolom ayah/ibu/wali menjadi baris terpisah
                val punyaDataLama = try {
                    val c = db.rawQuery(
                        "SELECT id_siswa, nama_ayah, telpon_ayah, nama_ibu, telpon_ibu, nama_wali, telpon_wali FROM orang_tua",
                        null
                    )
                    while (c.moveToNext()) {
                        fun tambahBaris(namaIdx: Int, hpIdx: Int, status: String) {
                            val nm = c.getString(namaIdx)?.trim().orEmpty()
                            if (nm.isNotEmpty()) {
                                val cvOld = ContentValues()
                                cvOld.put("id_siswa", c.getInt(0))
                                cvOld.put("nama", nm)
                                cvOld.put("no_hp", c.getString(hpIdx)?.trim().orEmpty())
                                cvOld.put("status", status)
                                db.insert("orang_tua_new", null, cvOld)
                            }
                        }
                        tambahBaris(1, 2, "Ayah")
                        tambahBaris(3, 4, "Ibu")
                        tambahBaris(5, 6, "Wali")
                    }
                    c.close()
                    true
                } catch (e: Exception) { false }
                db.execSQL("DROP TABLE IF EXISTS orang_tua")
                db.execSQL("ALTER TABLE orang_tua_new RENAME TO orang_tua")
                db.setTransactionSuccessful()
                Log.d("DB_UPGRADE", "Migrasi v16 sukses: struktur tabel orang_tua disederhanakan (data lama ikut dimigrasi: $punyaDataLama)")
            } catch (e: Exception) {
                Log.e("DB_UPGRADE", "Gagal migrasi v16 tabel orang_tua", e)
            } finally {
                db.endTransaction()
            }
        }
    }

    // ---- GURU ----
    fun updateGuru(nama: String, foto: String) {
        writableDatabase.execSQL("DELETE FROM guru")
        writableDatabase.execSQL("INSERT INTO guru (nama, foto) VALUES (?,?)", arrayOf(nama, foto))
    }

    fun getGuru(): Map<String, String>? {
        val c = readableDatabase.rawQuery("SELECT nama, foto FROM guru LIMIT 1", null)
        val r = if (c.moveToFirst()) mapOf("nama" to (c.getString(0)?: ""), "foto" to (c.getString(1)?: "")) else null
        c.close(); return r
    }

    //---- PENCARIAN ----
    data class HasilCari(val tipe: String, val id: String, val nama: String, val detail: String, val persenHadir: Double, val rataNilai: Double)

    fun searchGlobal(keyword: String): List<HasilCari> {
        val db = readableDatabase
        val list = mutableListOf<HasilCari>()
        val key = "%$keyword%"

        // 1. QUERY SISWA
        db.rawQuery("""
        SELECT s.nis, s.nama_siswa, k.nama_kelas,
        IFNULL(SUM(CASE WHEN a.status='Hadir' THEN 1 ELSE 0 END)*100.0/NULLIF(COUNT(a.id),0),0),
        IFNULL(AVG(n.nilai),0)
        FROM siswa s LEFT JOIN kelas k ON s.id_kelas=k.id
        LEFT JOIN absensi a ON a.id_siswa=s.id
        LEFT JOIN nilai n ON n.id_siswa=s.id
        WHERE s.nama_siswa LIKE ? OR s.nis LIKE ? GROUP BY s.id LIMIT 10
    """, arrayOf(key, key)).use { c ->
            while (c.moveToNext()) {
                val nis = c.getString(0) ?: ""
                val namaSiswa = c.getString(1) ?: ""
                val namaKelas = c.getString(2) ?: "-"
                val absensi = if (c.isNull(3)) 0.0 else c.getDouble(3)
                val nilai = if (c.isNull(4)) 0.0 else c.getDouble(4)

                list.add(HasilCari("SISWA", nis, namaSiswa, "NIS $nis • $namaKelas", absensi, nilai))
            }
        }

        // 2. QUERY KELAS
        db.rawQuery("""
        SELECT k.id, k.nama_kelas, COUNT(DISTINCT s.id),
        IFNULL(SUM(CASE WHEN a.status='Hadir' THEN 1 ELSE 0 END)*100.0/NULLIF(COUNT(a.id),0),0),
        IFNULL(AVG(n.nilai),0)
        FROM kelas k LEFT JOIN siswa s ON s.id_kelas=k.id
        LEFT JOIN absensi a ON a.id_siswa=s.id
        LEFT JOIN nilai n ON n.id_siswa=s.id
        WHERE k.nama_kelas LIKE ? GROUP BY k.id LIMIT 10
    """, arrayOf(key)).use { c ->
            while (c.moveToNext()) {
                val idKelas = c.getString(0) ?: ""
                val namaKelas = c.getString(1) ?: ""
                val jumlahSiswa = c.getInt(2)
                val absensi = if (c.isNull(3)) 0.0 else c.getDouble(3)
                val nilai = if (c.isNull(4)) 0.0 else c.getDouble(4)

                list.add(HasilCari("KELAS", idKelas, namaKelas, "$jumlahSiswa siswa", absensi, nilai))
            }
        }

        // 3. QUERY JURUSAN
        db.rawQuery("""
        SELECT SUBSTR(k.nama_kelas,4,3) as jur, COUNT(DISTINCT s.id),
        IFNULL(SUM(CASE WHEN a.status='Hadir' THEN 1 ELSE 0 END)*100.0/NULLIF(COUNT(a.id),0),0),
        IFNULL(AVG(n.nilai),0)
        FROM kelas k LEFT JOIN siswa s ON s.id_kelas=k.id
        LEFT JOIN absensi a ON a.id_siswa=s.id
        LEFT JOIN nilai n ON n.id_siswa=s.id
        WHERE k.nama_kelas LIKE ? GROUP BY jur HAVING jur!='' AND jur IS NOT NULL LIMIT 10
    """, arrayOf(key)).use { c ->
            while (c.moveToNext()) {
                val jur = c.getString(0)?.trim() ?: ""
                if (jur.isNotEmpty()) {
                    val jumlahSiswa = c.getInt(1)
                    val absensi = if (c.isNull(2)) 0.0 else c.getDouble(2)
                    val nilai = if (c.isNull(3)) 0.0 else c.getDouble(3)

                    list.add(HasilCari("JURUSAN", jur, "Jurusan $jur", "$jumlahSiswa siswa", absensi, nilai))
                }
            }
        }
        return list
    }

    // ---- KELAS ----
    fun insertKelas(nama: String, jurusan: String, wali: String, wa: String, semester: String = "Ganjil") {
        writableDatabase.execSQL("INSERT INTO kelas (nama_kelas, jurusan, wali_kelas, nomor_wa_wali, semester) VALUES (?,?,?,?,?)", arrayOf(nama, jurusan, wali, wa, semester))
    }
    fun getAllKelas(): List<Pair<Int, String>> {
        val l = mutableListOf<Pair<Int, String>>()
        val c = readableDatabase.rawQuery("SELECT id, nama_kelas FROM kelas ORDER BY nama_kelas", null)
        while (c.moveToNext()) l.add(Pair(c.getInt(0), c.getString(1))); c.close(); return l
    }
    fun getAllKelasLengkap(): List<Map<String, String>> {
        val l = mutableListOf<Map<String, String>>()
        val c = readableDatabase.rawQuery("SELECT * FROM kelas", null)
        while (c.moveToNext()) {
            val semesterIdx = c.getColumnIndex("semester")
            val semester = if (semesterIdx!= -1) c.getString(semesterIdx)?: "Ganjil" else "Ganjil"
            l.add(mapOf("id" to c.getInt(0).toString(), "nama_kelas" to (c.getString(1)?: ""), "jurusan" to (c.getString(2)?: ""), "wali_kelas" to (c.getString(3)?: ""), "nomor_wa_wali" to (c.getString(4)?: ""), "semester" to semester))
        }; c.close(); return l
    }
    fun deleteKelas(id: Int) { writableDatabase.delete("kelas", "id=?", arrayOf(id.toString())) }
    fun getTelpWaliByKelas(id: Int): String {
        val c = readableDatabase.rawQuery("SELECT nomor_wa_wali FROM kelas WHERE id=?", arrayOf(id.toString()))
        val t = if (c.moveToFirst()) c.getString(0)?: "" else ""; c.close(); return t
    }
    fun bersihkanNamaKelas() {
        val db = writableDatabase
        val c = db.rawQuery("SELECT id, nama_kelas FROM kelas", null)
        while (c.moveToNext()) {
            val id = c.getInt(0); val nama = c.getString(1)?: ""
            val parts = nama.split(" - ").map { it.trim() }
            val tahun = parts.lastOrNull { it.matches(Regex("\\d{4}/\\d{4}")) }?: ""
            val namaBersih = parts.firstOrNull()?: nama
            val namaBaru = if (tahun.isNotEmpty()) "$namaBersih - $tahun" else namaBersih
            db.execSQL("UPDATE kelas SET nama_kelas =? WHERE id =?", arrayOf(namaBaru, id))
        }; c.close()
    }
    fun getKelasById(id: Int): Map<String, String>? {
        val c = readableDatabase.rawQuery("SELECT nama_kelas, jurusan, wali_kelas, nomor_wa_wali FROM kelas WHERE id=?", arrayOf(id.toString()))
        val result = if (c.moveToFirst()) mapOf("nama" to c.getString(0), "jurusan" to c.getString(1), "wali" to c.getString(2), "wa" to c.getString(3)) else null
        c.close(); return result
    }
    fun updateKelas(id: Int, nama: String, jurusan: String, wali: String, wa: String, semester: String = "Ganjil"): Int {
        val cv = ContentValues().apply { put("nama_kelas", nama); put("jurusan", jurusan); put("wali_kelas", wali); put("nomor_wa_wali", wa); put("semester", semester) }
        return writableDatabase.update("kelas", cv, "id=?", arrayOf(id.toString()))
    }

    // ---- SISWA ----
    fun getSiswaByKelas(idKelas: Int): List<SiswaModel> {
        val list = mutableListOf<SiswaModel>()
        val c = readableDatabase.rawQuery("SELECT id, nama_siswa, nis, jenis_kelamin FROM siswa WHERE id_kelas=?", arrayOf(idKelas.toString()))
        while (c.moveToNext()) { list.add(SiswaModel(c.getInt(0), c.getString(1), c.getString(2), c.getString(3), idKelas)) }
        c.close(); return list
    }

    fun insertSiswa(idKelas: Int, nama: String, nis: String?, jk: String) {
        // kalau nis null, kita insert NULL beneran, bukan string "-"
        if (nis == null) {
            writableDatabase.execSQL(
                "INSERT INTO siswa (id_kelas, nama_siswa, nis, jenis_kelamin) VALUES (?,?,NULL,?)",
                arrayOf(idKelas, nama, jk)
            )
        } else {
            writableDatabase.execSQL(
                "INSERT INTO siswa (id_kelas, nama_siswa, nis, jenis_kelamin) VALUES (?,?,?,?)",
                arrayOf(idKelas, nama, nis, jk)
            )
        }
    }
    fun deleteSiswa(id: Int): Int { return writableDatabase.delete("siswa", "id=?", arrayOf(id.toString())) }
    fun updateSiswa(id: Int, nama: String, nis: String?, jk: String): Int {
        val cv = ContentValues()
        cv.put("nama_siswa", nama)
        if (nis == null) cv.putNull("nis") else cv.put("nis", nis)
        cv.put("jenis_kelamin", jk)
        return writableDatabase.update("siswa", cv, "id=?", arrayOf(id.toString()))
    }

    fun isNisExists(nis: String): Boolean {
        if (nis.isBlank()) return false
        val c = readableDatabase.rawQuery("SELECT 1 FROM siswa WHERE nis=? LIMIT 1", arrayOf(nis))
        val ada = c.moveToFirst()
        c.close()
        return ada
    }

    // ---- ORANG TUA / WALI (nama, no HP, status) ----
    fun insertOrangTua(data: OrangTuaModel): Long {
        val cv = ContentValues().apply {
            put("id_siswa", data.idSiswa)
            put("nama", data.nama)
            put("no_hp", data.noHp)
            put("status", data.status)
        }
        return writableDatabase.insert("orang_tua", null, cv)
    }

    fun updateOrangTua(id: Int, data: OrangTuaModel): Int {
        val cv = ContentValues().apply {
            put("id_siswa", data.idSiswa)
            put("nama", data.nama)
            put("no_hp", data.noHp)
            put("status", data.status)
        }
        return writableDatabase.update("orang_tua", cv, "id=?", arrayOf(id.toString()))
    }

    fun getOrangTuaBySiswa(idSiswa: Int): List<OrangTuaModel> {
        val list = mutableListOf<OrangTuaModel>()
        val c = readableDatabase.rawQuery(
            "SELECT id, id_siswa, nama, no_hp, status FROM orang_tua WHERE id_siswa=? ORDER BY status",
            arrayOf(idSiswa.toString())
        )
        while (c.moveToNext()) list.add(mapRowKeOrangTua(c))
        c.close()
        return list
    }

        fun getAllOrangTua(): List<Map<String, String>> {
        val list = mutableListOf<Map<String, String>>()
        // Menggunakan LEFT JOIN agar data dengan id_siswa 0 (umum) tetap terbaca
        val c = readableDatabase.rawQuery("""
            SELECT o.id, o.id_siswa, IFNULL(s.nama_siswa, 'Kontak Umum'), IFNULL(s.nis,''), o.nama, IFNULL(o.no_hp,''), o.status
            FROM orang_tua o 
            LEFT JOIN siswa s ON o.id_siswa = s.id
            ORDER BY o.status DESC, o.nama ASC
        """, null)
        
        while (c.moveToNext()) {
            list.add(mapOf(
                "id" to c.getInt(0).toString(),
                "id_siswa" to c.getInt(1).toString(),
                "nama_siswa" to c.getString(2),
                "nis" to c.getString(3),
                "nama" to c.getString(4),
                "no_hp" to c.getString(5),
                "status" to c.getString(6)
            ))
        }
        c.close()
        return list
    }

    fun deleteOrangTuaById(id: Int): Int {
        return writableDatabase.delete("orang_tua", "id=?", arrayOf(id.toString()))
    }

    fun deleteOrangTuaBySiswa(idSiswa: Int): Int {
        return writableDatabase.delete("orang_tua", "id_siswa=?", arrayOf(idSiswa.toString()))
    }

    private fun mapRowKeOrangTua(c: android.database.Cursor): OrangTuaModel {
        return OrangTuaModel(
            id = c.getInt(0),
            idSiswa = c.getInt(1),
            nama = c.getString(2) ?: "",
            noHp = c.getString(3) ?: "",
            status = c.getString(4) ?: "Ayah"
        )
    }

    fun bulkInsertSiswa(data: List<ContentValues>) {
        val db = writableDatabase; db.beginTransaction()
        try { data.forEach { db.insert("siswa", null, it) }; db.setTransactionSuccessful() } finally { db.endTransaction() }
    }

    // ---- ABSENSI ----
    fun updateAtauSimpanAbsensi(idSiswa: Int, tanggal: String, status: String) {
        writableDatabase.execSQL("INSERT OR REPLACE INTO absensi (id_siswa, tanggal, status) VALUES (?,?,?)", arrayOf(idSiswa, tanggal, status))
    }
    fun getStatusAbsen(idSiswa: Int, tanggal: String): String {
        val c = readableDatabase.rawQuery("SELECT status FROM absensi WHERE id_siswa=? AND tanggal=?", arrayOf(idSiswa.toString(), tanggal))
        val s = if (c.moveToFirst()) c.getString(0)?: "Hadir" else "Hadir"; c.close(); return s
    }
    fun getStatusAbsensi(idSiswa: Int, tanggal: String): String? {
        val c = readableDatabase.rawQuery("SELECT status FROM absensi WHERE id_siswa=? AND tanggal=?", arrayOf(idSiswa.toString(), tanggal))
        val status = if (c.moveToFirst()) c.getString(0) else null; c.close(); return status
    }
    fun getAbsensiByTanggal(idKelas: Int, tanggal: String): Map<Int, String> {
        val m = mutableMapOf<Int, String>()
        val c = readableDatabase.rawQuery("SELECT s.id, a.status FROM siswa s LEFT JOIN absensi a ON s.id=a.id_siswa AND a.tanggal=? WHERE s.id_kelas=?", arrayOf(tanggal, idKelas.toString()))
        while (c.moveToNext()) m[c.getInt(0)] = c.getString(1)?: "Hadir"; c.close(); return m
    }

    fun getRekapAlphaBolos(idSiswa: Int): Pair<Int, Int> {
        val db = readableDatabase
        val c1 = db.rawQuery("SELECT COUNT(*) FROM absensi WHERE id_siswa=? AND status='Alpha'", arrayOf(idSiswa.toString())); c1.moveToFirst(); val a = c1.getInt(0); c1.close()
        val c2 = db.rawQuery("SELECT COUNT(*) FROM absensi WHERE id_siswa=? AND status='Bolos'", arrayOf(idSiswa.toString())); c2.moveToFirst(); val b = c2.getInt(0); c2.close()
        return Pair(a, b)
    }

    // ---- NILAI ----
    // === NILAI ===
    fun insertNilai(idSiswa: Int, mapel: String, nilai: Int, tanggal: String, keterangan: String) {
        writableDatabase.execSQL(
            "INSERT INTO nilai (id_siswa, mapel, nilai, tanggal, keterangan) VALUES (?,?,?,?,?)",
            arrayOf(idSiswa, mapel, nilai, tanggal, keterangan)
        )
    }

    data class NilaiDetail(val nilai: Double, val keterangan: String, val tanggal: String)

    fun getNilaiDetail(idSiswa: Int, mapel: String): NilaiDetail {
        val c = readableDatabase.rawQuery(
            "SELECT nilai, keterangan, tanggal FROM nilai WHERE id_siswa=? AND mapel=? ORDER BY tanggal DESC LIMIT 1",
            arrayOf(idSiswa.toString(), mapel)
        )
        val d = if(c.moveToFirst()) NilaiDetail(c.getDouble(0), c.getString(1)?: mapel, c.getString(2)?: "")
        else NilaiDetail(-1.0, mapel, "")
        c.close(); return d
    }

    fun getNilaiSiswa(idSiswa: Int, mapel: String, tahun: String = ""): Double {
        return getNilaiDetail(idSiswa, mapel).nilai
    }

    fun insertOrUpdateNilai(idSiswa: Int, mapel: String, tahun: String, nilai: Double, keterangan: String) {
        val db = writableDatabase
        val tgl = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val cv = ContentValues().apply {
            put("id_siswa", idSiswa); put("mapel", mapel); put("nilai", nilai.toInt())
            put("tanggal", tgl); put("keterangan", keterangan.ifBlank { mapel })
        }
        val upd = db.update("nilai", cv, "id_siswa=? AND mapel=?", arrayOf(idSiswa.toString(), mapel))
        if(upd == 0) db.insert("nilai", null, cv)
    }

    fun deleteNilai(idSiswa: Int, mapel: String, tahun: String = "") {
        writableDatabase.delete("nilai", "id_siswa=? AND mapel=?", arrayOf(idSiswa.toString(), mapel))
    }

    fun getNilaiBySiswa(idSiswa: Int): List<Map<String, String>> {
        val l = mutableListOf<Map<String, String>>()
        val c = readableDatabase.rawQuery(
            "SELECT mapel, nilai, tanggal, IFNULL(keterangan,'') FROM nilai WHERE id_siswa=? ORDER BY tanggal DESC",
            arrayOf(idSiswa.toString())
        )
        while(c.moveToNext()){
            l.add(mapOf("mapel" to c.getString(0), "nilai" to c.getInt(1).toString(),
                "tanggal" to c.getString(2), "keterangan" to c.getString(3)))
        }
        c.close(); return l
    }
    // ---- STATISTIK ----
    fun getStatistikKelas(idKelas: Int): Map<String, Int> {
        val m = mutableMapOf<String, Int>()
        val c = readableDatabase.rawQuery("SELECT status, COUNT(*) FROM absensi a JOIN siswa s ON a.id_siswa=s.id WHERE s.id_kelas=? GROUP BY status", arrayOf(idKelas.toString()))
        while (c.moveToNext()) m[c.getString(0)] = c.getInt(1); c.close(); return m
    }
    fun getDaftarTanggalAbsensi(idKelas: Int): List<String> {
        val l = mutableListOf<String>()
        val c = readableDatabase.rawQuery("SELECT DISTINCT a.tanggal FROM absensi a JOIN siswa s ON a.id_siswa=s.id WHERE s.id_kelas=? ORDER BY a.tanggal DESC", arrayOf(idKelas.toString()))
        while (c.moveToNext()) l.add(c.getString(0)); c.close(); return l
    }
    fun getRekapAbsensiPerTanggal(idKelas: Int): List<Map<String, String>> {
        val list = mutableListOf<Map<String, String>>()
        val c = readableDatabase.rawQuery("""
            SELECT a.tanggal,
                   SUM(CASE WHEN a.status='Hadir' THEN 1 ELSE 0 END),
                   SUM(CASE WHEN a.status='Sakit' THEN 1 ELSE 0 END),
                   SUM(CASE WHEN a.status='Izin' THEN 1 ELSE 0 END),
                   SUM(CASE WHEN a.status='Alpha' THEN 1 ELSE 0 END),
                   SUM(CASE WHEN a.status='Bolos' THEN 1 ELSE 0 END)
            FROM absensi a JOIN siswa s ON a.id_siswa=s.id
            WHERE s.id_kelas=? GROUP BY a.tanggal ORDER BY a.tanggal DESC
        """, arrayOf(idKelas.toString()))
        while (c.moveToNext()) {
            list.add(mapOf("tanggal" to c.getString(0), "Hadir" to c.getInt(1).toString(), "Sakit" to c.getInt(2).toString(), "Izin" to c.getInt(3).toString(), "Alpha" to c.getInt(4).toString(), "Bolos" to c.getInt(5).toString()))
        }; c.close(); return list
    }
    fun deleteAbsensiByTanggal(idKelas: Int, tanggal: String) {
        writableDatabase.execSQL("DELETE FROM absensi WHERE tanggal=? AND id_siswa IN (SELECT id FROM siswa WHERE id_kelas=?)", arrayOf(tanggal, idKelas.toString()))
    }
    fun getTotalSiswaByKelas(idKelas: Int): Int {
        val c = readableDatabase.rawQuery("SELECT COUNT(*) FROM siswa WHERE id_kelas=?", arrayOf(idKelas.toString())); c.moveToFirst(); val total = c.getInt(0); c.close(); return total
    }
    fun getAverageNilaiByKelas(idKelas: Int): Double {
        val c = readableDatabase.rawQuery("SELECT AVG(nilai) FROM nilai n JOIN siswa s ON n.id_siswa=s.id WHERE s.id_kelas=?", arrayOf(idKelas.toString())); c.moveToFirst(); val avg = if (c.isNull(0)) 0.0 else c.getDouble(0); c.close(); return avg
    }
    fun getAbsensiEntries(idKelas: Int): MutableList<PieEntry> {
        val list = mutableListOf<PieEntry>()
        val c = readableDatabase.rawQuery("SELECT status, COUNT(*) FROM absensi a JOIN siswa s ON a.id_siswa=s.id WHERE s.id_kelas=? GROUP BY status", arrayOf(idKelas.toString()))
        while (c.moveToNext()) { list.add(PieEntry(c.getInt(1).toFloat(), c.getString(0))) }; c.close(); return list
    }
    fun getTop5NilaiSiswaLengkap(idKelas: Int): Pair<MutableList<BarEntry>, List<String>> {
        val entries = mutableListOf<BarEntry>(); val names = mutableListOf<String>()
        val c = readableDatabase.rawQuery("SELECT s.nama_siswa, AVG(n.nilai) AS rata FROM nilai n JOIN siswa s ON n.id_siswa = s.id WHERE s.id_kelas =? GROUP BY s.id ORDER BY rata DESC LIMIT 5", arrayOf(idKelas.toString()))
        var index = 0f
        while (c.moveToNext()) { names.add(c.getString(0)); entries.add(BarEntry(index, c.getFloat(1))); index += 1f }; c.close(); return Pair(entries, names)
    }
    fun getStatistikPerSiswa(idKelas: Int): List<SiswaStatistikModel> {
        val list = mutableListOf<SiswaStatistikModel>()
        val c = readableDatabase.rawQuery("""
            SELECT s.id, s.nama_siswa,
            IFNULL(SUM(CASE WHEN a.status='Hadir' THEN 1 ELSE 0 END),0),
            IFNULL(SUM(CASE WHEN a.status='Izin' THEN 1 ELSE 0 END),0),
            IFNULL(SUM(CASE WHEN a.status='Sakit' THEN 1 ELSE 0 END),0),
            IFNULL(SUM(CASE WHEN a.status='Alpha' THEN 1 ELSE 0 END),0),
            IFNULL(SUM(CASE WHEN a.status='Bolos' THEN 1 ELSE 0 END),0),
            COUNT(a.id), IFNULL(AVG(n.nilai),0)
            FROM siswa s LEFT JOIN absensi a ON s.id = a.id_siswa LEFT JOIN nilai n ON s.id = n.id_siswa
            WHERE s.id_kelas =? GROUP BY s.id ORDER BY s.nama_siswa
        """, arrayOf(idKelas.toString()))
        while (c.moveToNext()) {
            val hadir = c.getInt(2); val total = c.getInt(7); val persen = if (total > 0) hadir * 100f / total else 0f
            list.add(SiswaStatistikModel(c.getInt(0), c.getString(1), hadir, c.getInt(3), c.getInt(4), c.getInt(5), c.getInt(6), persen, c.getDouble(8)))
        }; c.close(); return list
    }
    fun getRekapBulanan(idSiswa: Int, bulan: Int, tahun: Int): Pair<Int, Int> {
        val db = readableDatabase; val bulanStr = String.format("%02d", bulan)
        val c = db.rawQuery("""
            SELECT SUM(CASE WHEN status='Alpha' THEN 1 ELSE 0 END), SUM(CASE WHEN status='Bolos' THEN 1 ELSE 0 END)
            FROM absensi WHERE id_siswa=? AND strftime('%m', tanggal)=? AND strftime('%Y', tanggal)=?
        """, arrayOf(idSiswa.toString(), bulanStr, tahun.toString()))
        var a = 0; var b = 0; if (c.moveToFirst()) { a = c.getInt(0); b = c.getInt(1) }; c.close(); return Pair(a, b)
    }
    fun sudahDikirimBulanIni(idSiswa: Int, bulan: Int, tahun: Int): Boolean {
        val c = readableDatabase.rawQuery("SELECT 1 FROM log_notifikasi WHERE id_siswa=? AND bulan=? AND tahun=?", arrayOf(idSiswa.toString(), bulan.toString(), tahun.toString()))
        val ada = c.count > 0; c.close(); return ada
    }
    fun catatPengiriman(idSiswa: Int, bulan: Int, tahun: Int, alpha: Int, bolos: Int) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("id_siswa", idSiswa); put("bulan", bulan); put("tahun", tahun); put("alpha", alpha); put("bolos", bolos)
            put("tanggal_kirim", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(
                Date()
            ))
        }
        db.insertWithOnConflict("log_notifikasi", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getRiwayatAbsensiSiswa(idSiswa: Int): List<Map<String, String>> {
        val list = mutableListOf<Map<String, String>>()
        val c = readableDatabase.rawQuery(
            "SELECT tanggal, status FROM absensi WHERE id_siswa=? ORDER BY tanggal DESC LIMIT 50", // FIX: batasi 50 biar ringan
            arrayOf(idSiswa.toString())
        )
        while (c.moveToNext()) {
            val tgl = c.getString(0)
            val st = c.getString(1)?: "-"
            list.add(mapOf(
                "tanggal" to tgl,
                "Hadir" to if(st=="Hadir") "1" else "0",
                "Sakit" to if(st=="Sakit") "1" else "0",
                "Izin" to if(st=="Izin") "1" else "0",
                "Alpha" to if(st=="Alpha") "1" else "0",
                "Bolos" to if(st=="Bolos") "1" else "0"
            ))
        }
        c.close()
        return list
    }

    // FUNGSI BARU UNTUK PROFIL SISWA - DIPERBAIKI
    fun getStatistikSiswa(idSiswa: Int): Map<String, Int> { // FIX: ubah ke Int
        val stats = mutableMapOf("hadir" to 0, "izin" to 0, "sakit" to 0, "alpha" to 0, "bolos" to 0)
        val c = readableDatabase.rawQuery(
            "SELECT LOWER(status), COUNT(*) FROM absensi WHERE id_siswa=? GROUP BY LOWER(status)",
            arrayOf(idSiswa.toString())
        )
        while (c.moveToNext()) {
            val key = if (c.getString(0) == "alpa") "alpha" else c.getString(0)
            stats[key] = c.getInt(1)
        }
        c.close()
        return stats
    }

    fun getNilaiSiswa(idSiswa: Int, jenis: String): Int {
        // FIX: tabel kamu pakai id_siswa, mapel, keterangan - bukan siswa_id dan jenis
        val c = readableDatabase.rawQuery(
            """SELECT nilai FROM nilai
               WHERE id_siswa=? AND (UPPER(keterangan)=? OR UPPER(mapel)=?)
               ORDER BY tanggal DESC LIMIT 1""",
            arrayOf(idSiswa.toString(), jenis.uppercase(), jenis.uppercase())
        )
        val hasil = if (c.moveToFirst()) c.getInt(0) else 0
        c.close()
        Log.d("DB_NILAI", "cari id=$idSiswa jenis=$jenis -> $hasil")
        return hasil
    }

    // FIX: fungsi baru untuk rata-rata
    fun getSemuaNilai(idSiswa: Int): List<Int> {
        val list = mutableListOf<Int>()
        val c = readableDatabase.rawQuery("SELECT nilai FROM nilai WHERE id_siswa=?", arrayOf(idSiswa.toString()))
        while (c.moveToNext()) list.add(c.getInt(0))
        c.close()
        return list
    }

        // Ambil data orang tua yang belum di-assign ke siswa manapun (id_siswa = 0)
    fun getOrangTuaGlobal(): List<OrangTuaModel> {
        val list = mutableListOf<OrangTuaModel>()
        val c = readableDatabase.rawQuery(
            "SELECT id, id_siswa, nama, no_hp, status FROM orang_tua WHERE id_siswa = 0", 
            null
        )
        while (c.moveToNext()) list.add(mapRowKeOrangTua(c))
        c.close()
        return list
    }

    // Menautkan orang tua yang ada di database ke siswa tertentu
    fun linkOrangTuaKeSiswa(idOrangTua: Int, idSiswaBaru: Int): Int {
        val cv = ContentValues().apply { put("id_siswa", idSiswaBaru) }
        return writableDatabase.update("orang_tua", cv, "id=?", arrayOf(idOrangTua.toString()))
    }
    
}
