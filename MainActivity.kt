package kz.fermerkz.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

class MainActivity : FragmentActivity(), OnMapReadyCallback, GoogleMap.OnMapClickListener {
    private lateinit var mapFrame: FrameLayout
    private lateinit var marketView: ScrollView
    private lateinit var publishView: ScrollView
    private lateinit var map: GoogleMap
    private lateinit var status: TextView
    private lateinit var areaText: TextView
    private lateinit var perimeterText: TextView
    private lateinit var nameInput: EditText
    private lateinit var fused: com.google.android.gms.location.FusedLocationProviderClient
    private val points = mutableListOf<LatLng>()
    private val savedFields = mutableListOf<Pair<String, Double>>()
    private val listings = mutableListOf<JSONObject>()
    private var polygon: Polygon? = null
    private val prefs by lazy { getSharedPreferences("fermer_kz", MODE_PRIVATE) }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
                enableLocation()
                addGpsPoint()
            } else {
                status.text = "Разрешение GPS не предоставлено."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fused = LocationServices.getFusedLocationProviderClient(this)
        loadLocalData()
        buildInterface()
        val fragment = SupportMapFragment.newInstance()
        supportFragmentManager.beginTransaction().replace(mapFrame.id, fragment).commit()
        fragment.getMapAsync(this)
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(245, 248, 242))
        }
        val header = TextView(this).apply {
            text = "🌾  ФЕРМЕР KZ\\nЗемля • Картометр • Объявления"
            textSize = 19f
            setTextColor(Color.WHITE)
            setPadding(18, 16, 18, 16)
            setBackgroundColor(Color.rgb(28, 91, 49))
        }
        root.addView(header)
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tabs.addView(tabButton("🗺 Карта") { showPage("map") }, weight())
        tabs.addView(tabButton("Земля") { showPage("market") }, weight())
        tabs.addView(tabButton("＋ Подать") { showPage("publish") }, weight())
        root.addView(tabs)

        mapFrame = FrameLayout(this).apply { id = View.generateViewId() }
        root.addView(mapFrame, LinearLayout.LayoutParams(-1, 0, 1f))

        marketView = ScrollView(this)
        marketView.addView(buildMarket())
        root.addView(marketView, LinearLayout.LayoutParams(-1, 0, 1f))
        publishView = ScrollView(this)
        publishView.addView(buildPublish())
        root.addView(publishView, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        showPage("map")
    }

    private fun weight() = LinearLayout.LayoutParams(0, dp(48), 1f)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tabButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 12f
        setOnClickListener { action() }
    }

    private fun showPage(page: String) {
        mapFrame.visibility = if (page == "map") View.VISIBLE else View.GONE
        marketView.visibility = if (page == "market") View.VISIBLE else View.GONE
        publishView.visibility = if (page == "publish") View.VISIBLE else View.GONE
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.mapType = GoogleMap.MAP_TYPE_SATELLITE
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isCompassEnabled = true
        map.setOnMapClickListener(this)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(48.0, 67.0), 4.5f))
        enableLocationIfAllowed()
    }

    override fun onMapClick(point: LatLng) {
        points.add(point)
        redrawPolygon()
        updateMeasure()
    }

    private fun redrawPolygon() {
        if (!::map.isInitialized) return
        polygon?.remove()
        map.clear()
        points.forEachIndexed { index, p ->
            map.addMarker(MarkerOptions().position(p).title("Точка ${index + 1}"))
        }
        if (points.size >= 2) {
            val opts = PolygonOptions().addAll(points).strokeColor(Color.rgb(26, 110, 55))
                .strokeWidth(4f).fillColor(0x5539A653).geodesic(true)
            if (points.size >= 3) polygon = map.addPolygon(opts)
            else map.addPolyline(PolylineOptions().addAll(points).color(Color.rgb(26, 110, 55)).width(4f))
        }
    }

    private fun updateMeasure() {
        val a = if (points.size >= 3) sphericalArea(points) else 0.0
        val p = if (points.size >= 2) perimeter(points) else 0.0
        areaText.text = "Площадь: %.4f га  (%.1f м²)".format(a / 10000.0, a)
        perimeterText.text = "Периметр: %.1f м • Точек: %d".format(p, points.size)
        status.text = if (points.size < 3) "Отмечай углы участка на спутниковой карте." else "Граница отмечена. Нажми «Замкнуть и сохранить»."
    }

    private fun sphericalArea(path: List<LatLng>): Double {
        val radius = 6378137.0
        var sum = 0.0
        for (i in path.indices) {
            val a = path[i]
            val b = path[(i + 1) % path.size]
            sum += Math.toRadians(b.longitude - a.longitude) *
                (2.0 + sin(Math.toRadians(a.latitude)) + sin(Math.toRadians(b.latitude)))
        }
        return abs(sum * radius * radius / 2.0)
    }

    private fun perimeter(path: List<LatLng>): Double {
        var total = 0f
        for (i in path.indices) {
            val a = FloatArray(1)
            val b = path[i]
            val c = path[(i + 1) % path.size]
            Location.distanceBetween(b.latitude, b.longitude, c.latitude, c.longitude, a)
            total += a[0]
        }
        return total.toDouble()
    }

    private fun buildMarket(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(18))
        }
        box.addView(label("Земля на продажу и в аренду"))
        val search = EditText(this).apply { hint = "Область, район или село" }
        box.addView(search)
        val filter = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Все объявления", "Продажа", "Аренда"))
        }
        box.addView(filter)
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(results)
        fun refresh() {
            results.removeAllViews()
            val demo = listOf(
                JSONObject().put("type", "Продажа").put("title", "Демонстрационная пашня").put("place", "Казахстан").put("ha", 10).put("price", "Цена договорная"),
                JSONObject().put("type", "Аренда").put("title", "Демонстрационное пастбище").put("place", "Казахстан").put("ha", 5).put("price", "Цена договорная")
            )
            (listings + demo).filter {
                (filter.selectedItemPosition == 0 || it.optString("type") == filter.selectedItem.toString()) &&
                    (search.text.isNullOrBlank() || (it.optString("place") + it.optString("title")).contains(search.text.toString(), true))
            }.forEach { ad ->
                results.addView(card("${ad.optString("title")}\\n${ad.optString("type")} • ${ad.optString("place")}\\n${ad.optString("ha")} га • ${ad.optString("price")}"))
            }
        }
        val button = Button(this).apply { text = "Найти"; setOnClickListener { refresh() } }
        box.addView(button)
        filter.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { refresh() }
        }
        refresh()
        return box
    }

    private fun buildPublish(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(18))
        }
        box.addView(label("Разместить участок"))
        val type = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Продажа", "Аренда"))
        }
        val title = input("Название участка")
        val place = input("Область, район, село")
        val hectares = input("Площадь в гектарах")
        val price = input("Цена в тенге")
        val phone = input("Телефон для связи")
        listOf(type as View, title, place, hectares, price, phone).forEach { box.addView(it) }
        box.addView(Button(this).apply {
            text = "Опубликовать объявление"
            setOnClickListener {
                if (title.text.isNullOrBlank() || place.text.isNullOrBlank() || hectares.text.isNullOrBlank()) {
                    Toast.makeText(this@MainActivity, "Заполни название, место и площадь", Toast.LENGTH_SHORT).show()
                } else {
                    val ad = JSONObject().put("type", type.selectedItem.toString())
                        .put("title", title.text.toString()).put("place", place.text.toString())
                        .put("ha", hectares.text.toString()).put("price", price.text.toString())
                        .put("phone", phone.text.toString())
                    listings.add(0, ad)
                    saveLocalData()
                    Toast.makeText(this@MainActivity, "Объявление сохранено на этом телефоне", Toast.LENGTH_LONG).show()
                    showPage("market")
                }
            }
        })
        box.addView(label("Пока объявления хранятся только на этом устройстве. Для общего каталога понадобится сервер."))
        return box
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 17f
        setTextColor(Color.rgb(35, 75, 45))
        setPadding(0, dp(10), 0, dp(8))
    }

    private fun input(hintText: String) = EditText(this).apply {
        hint = hintText
        textSize = 15f
        setSingleLine(true)
    }

    private fun card(text: String) = TextView(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(Color.DKGRAY)
        setPadding(dp(12), dp(14), dp(12), dp(14))
        setBackgroundColor(Color.WHITE)
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.setMargins(0, dp(6), 0, dp(6))
        layoutParams = lp
    }

    private fun buildMapControls() {
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            gravity = Gravity.BOTTOM
        }
        nameInput = EditText(this).apply { hint = "Название участка"; setSingleLine(true) }
        areaText = TextView(this).apply { text = "Площадь: 0 га"; setTextColor(Color.WHITE); setPadding(dp(8), dp(5), dp(8), dp(5)); setBackgroundColor(0xAA174B2D.toInt()) }
        perimeterText = TextView(this).apply { text = "Периметр: 0 м"; setTextColor(Color.WHITE); setPadding(dp(8), dp(5), dp(8), dp(5)); setBackgroundColor(0xAA174B2D.toInt()) }
        status = TextView(this).apply { text = "Отмечай углы участка на карте."; setTextColor(Color.WHITE); setPadding(dp(8), dp(5), dp(8), dp(5)); setBackgroundColor(0xAA174B2D.toInt()) }
        controls.addView(areaText); controls.addView(perimeterText); controls.addView(status); controls.addView(nameInput)
        controls.addView(Button(this).apply {
            text = "Спутник / обычная карта"
            setOnClickListener { if (::map.isInitialized) map.mapType = if (map.mapType == GoogleMap.MAP_TYPE_SATELLITE) GoogleMap.MAP_TYPE_NORMAL else GoogleMap.MAP_TYPE_SATELLITE }
        })
        controls.addView(Button(this).apply {
            text = "GPS: моё местоположение"
            setOnClickListener { requestOrEnableLocation() }
        })
        controls.addView(Button(this).apply {
            text = "↶ Отменить точку"
            setOnClickListener { if (points.isNotEmpty()) points.removeAt(points.lastIndex); redrawPolygon(); updateMeasure() }
        })
        controls.addView(Button(this).apply {
            text = "Замкнуть и сохранить"
            setOnClickListener {
                if (points.size < 3) {
                    Toast.makeText(this@MainActivity, "Нужно минимум 3 точки", Toast.LENGTH_SHORT).show()
                } else {
                    redrawPolygon()
                    val name = nameInput.text.toString().ifBlank { "Участок ${savedFields.size + 1}" }
                    val ha = sphericalArea(points) / 10000.0
                    savedFields.add(name to ha)
                    saveLocalData()
                    Toast.makeText(this@MainActivity, "$name сохранён: %.4f га".format(ha), Toast.LENGTH_LONG).show()
                }
            }
        })
        controls.addView(Button(this).apply {
            text = "Очистить карту"
            setOnClickListener { points.clear(); redrawPolygon(); updateMeasure() }
        })
        mapFrame.addView(controls, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
    }

    private fun requestOrEnableLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enableLocation()
            addGpsPoint()
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private fun enableLocationIfAllowed() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) enableLocation()
        buildMapControls()
    }

    private fun enableLocation() {
        if (!::map.isInitialized) return
        try {
            map.isMyLocationEnabled = true
        } catch (_: SecurityException) { }
    }

    private fun addGpsPoint() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        try {
            fused.lastLocation.addOnSuccessListener { loc: Location? ->
                if (loc == null) {
                    Toast.makeText(this, "GPS пока не определил координаты. Подожди немного и попробуй снова.", Toast.LENGTH_LONG).show()
                } else {
                    val p = LatLng(loc.latitude, loc.longitude)
                    points.add(p)
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(p, 17f))
                    redrawPolygon()
                    updateMeasure()
                }
            }
        } catch (_: SecurityException) { }
    }

    private fun loadLocalData() {
        val arr = JSONArray(prefs.getString("listings", "[]"))
        for (i in 0 until arr.length()) listings.add(arr.getJSONObject(i))
        val fields = JSONArray(prefs.getString("fields", "[]"))
        for (i in 0 until fields.length()) {
            val o = fields.getJSONObject(i)
            savedFields.add(o.getString("name") to o.getDouble("ha"))
        }
    }

    private fun saveLocalData() {
        val ads = JSONArray()
        listings.forEach { ads.put(it) }
        val fields = JSONArray()
        savedFields.forEach { (name, ha) -> fields.put(JSONObject().put("name", name).put("ha", ha)) }
        prefs.edit().putString("listings", ads.toString()).putString("fields", fields.toString()).apply()
    }
}
