import SwiftUI

struct Quote: Identifiable {
    let id: String
    let name: String
    let price: Double
    let change: Double
    static let demo: [Quote] = [
        .init(id: "SPX", name: "标普500", price: 5648.40, change: 30.26),
        .init(id: "IXIC", name: "纳斯达克", price: 17713.62, change: 146.03),
        .init(id: "HSI", name: "恒生指数", price: 17444.30, change: -67.86),
        .init(id: "SSE", name: "上证指数", price: 2765.81, change: -4.67),
        .init(id: "BTC", name: "比特币", price: 58214, change: 713),
        .init(id: "NVDA", name: "英伟达", price: 119.10, change: 2.24),
        .init(id: "AAPL", name: "苹果", price: 222.50, change: 0.40),
        .init(id: "TSLA", name: "特斯拉", price: 226.17, change: -3.49),
        .init(id: "BABA", name: "阿里巴巴", price: 84.69, change: -0.63)
    ]
    func values(_ period: Int) -> [Double] {
        let index = Self.demo.firstIndex(where: { $0.id == id }) ?? 0
        let delta = period == 0 ? change : price * [0, 0.022, -0.037, 0.19][period] * (index % 2 == 0 ? 1 : -1)
        return (0...60).map { i in
            let t = Double(i) / 60
            let ripple = (sin(Double(i) * 0.72 + Double(index)) + 0.7 * sin(Double(i) * 0.27 + Double(period))) * price * 0.0008 * sin(.pi * t)
            return price - delta + delta * t + ripple
        }
    }
}

@main struct MarketWallApp: App {
    var body: some Scene { WindowGroup { Dashboard().preferredColorScheme(.dark) } }
}

struct Dashboard: View {
    @State private var selected = Quote.demo[0]
    @State private var period = 0
    @State private var favoritesOnly = false
    @State private var information = false
    @AppStorage("market-wall-favorites") private var saved = "NVDA,AAPL,TSLA,BABA"
    private let periods = ["1日", "1周", "1月", "1年"]
    private var favorites: [String] { saved.split(separator: ",").map(String.init) }
    private var values: [Double] { selected.values(period) }
    private var delta: Double { (values.last ?? 0) - (values.first ?? 0) }
    private var tint: Color { delta >= 0 ? .mint : .red }
    private func number(_ value: Double) -> String { String(format: "%.2f", value) }
    private func toggleFavorite() {
        var list = favorites
        if list.contains(selected.id) { list.removeAll { $0 == selected.id } }
        else { list.append(selected.id) }
        saved = list.joined(separator: ",")
    }

    var body: some View {
        GeometryReader { geometry in
            VStack(alignment: .leading, spacing: 24) {
                HStack {
                    VStack(alignment: .leading) {
                        Text("MARKET WALL").font(.system(size: 32, weight: .bold, design: .serif))
                        Text("全球金融看板 · Apple TV").foregroundColor(.secondary)
                    }
                    Spacer()
                    TimelineView(.periodic(from: .now, by: 1)) { context in
                        Text(context.date, style: .time).monospacedDigit()
                    }
                    Button("资讯与日历") { information = true }
                }
                HStack(spacing: 28) {
                    ForEach(Quote.demo.prefix(4)) { quote in
                        Button { selected = quote } label: {
                            VStack(alignment: .leading, spacing: 6) {
                                Text(quote.name).font(.headline)
                                Text(number(quote.price)).monospacedDigit()
                                Text(String(format: "%+.2f", quote.change)).foregroundColor(quote.change >= 0 ? .mint : .red)
                            }.frame(maxWidth: .infinity, alignment: .leading)
                        }.tint(selected.id == quote.id ? .mint : .gray)
                    }
                }
                HStack(alignment: .top, spacing: 32) {
                    VStack(alignment: .leading, spacing: 16) {
                        HStack {
                            VStack(alignment: .leading) {
                                Text(selected.name + " / " + selected.id).foregroundColor(.secondary)
                                Text(number(selected.price)).font(.system(size: 54, weight: .semibold, design: .rounded)).monospacedDigit()
                            }
                            Spacer()
                            Text(String(format: "%+.2f / %+.2f%%", delta, delta / (values.first ?? 1) * 100)).foregroundColor(tint)
                        }
                        HStack(spacing: 28) {
                            ForEach(0..<4) { index in
                                Button(periods[index] + (period == index ? " ✓" : "")) { period = index }
                                    .tint(period == index ? .mint : .gray)
                            }
                            Spacer()
                        }
                        ChartLine(values: values).stroke(tint, style: StrokeStyle(lineWidth: 4, lineJoin: .round))
                            .background(Color.white.opacity(0.025))
                            .accessibilityLabel("\(selected.name)\(periods[period])演示走势图")
                            .frame(maxHeight: .infinity)
                        HStack {
                            Text("区间起点 " + number(values.first ?? 0))
                            Spacer()
                            Text("最高 " + number(values.max() ?? 0))
                            Spacer()
                            Text("最低 " + number(values.min() ?? 0))
                        }.font(.system(size: 18)).foregroundColor(.secondary)
                        Button(favorites.contains(selected.id) ? "移出自选" : "加入自选") { toggleFavorite() }
                    }.padding(24).background(Color.white.opacity(0.04)).cornerRadius(20)
                    VStack(alignment: .leading, spacing: 18) {
                        Button(favoritesOnly ? "自选 · 显示全部" : "全部行情 · 仅看自选") { favoritesOnly.toggle() }
                        ScrollView {
                            VStack(spacing: 20) {
                                ForEach(Quote.demo.filter { !favoritesOnly || favorites.contains($0.id) }) { quote in
                                    Button { selected = quote } label: {
                                        HStack {
                                            Text(quote.id)
                                            Spacer()
                                            Text(number(quote.price)).monospacedDigit()
                                        }.font(.system(size: 22)).padding(4)
                                    }.tint(selected.id == quote.id ? .mint : .gray)
                                }
                                if favoritesOnly && favorites.isEmpty { Text("暂无自选，请在主图加入标的。").foregroundColor(.secondary) }
                            }.padding(12)
                        }
                    }.frame(width: 360)
                }
                Text("演示数据 · 非实时行情  |  遥控器滑动 / 方向键导航，点击选择，播放暂停键收藏  |  时钟为本地时间")
                    .font(.system(size: 16)).foregroundColor(.secondary)
            }
            .padding(48)
            .frame(width: geometry.size.width, height: geometry.size.height)
            .background(LinearGradient(colors: [Color(red: 0.025, green: 0.06, blue: 0.09), .black], startPoint: .topLeading, endPoint: .bottomTrailing))
        }
        .onPlayPauseCommand { toggleFavorite() }
        .sheet(isPresented: $information) {
            VStack(alignment: .leading, spacing: 32) {
                Text("市场资讯与经济日历").font(.largeTitle)
                Text("以下为演示内容，不连接新闻或经济数据服务。").foregroundColor(.secondary)
                Text("市场观察：科技股、全球指数与数字资产走势分化。")
                Text("日历示例：周三 · 通胀数据；周四 · 就业数据；周五 · 消费者信心。")
                Text("情绪示例：偏向乐观 · 62 / 100")
                Button("返回行情") { information = false }
            }.padding(80)
        }
    }
}

struct ChartLine: Shape {
    let values: [Double]
    func path(in rect: CGRect) -> Path {
        guard values.count > 1 else { return Path() }
        let low = values.min() ?? 0
        let span = max((values.max() ?? 0) - low, 0.0001)
        return Path { path in
            for (index, value) in values.enumerated() {
                let point = CGPoint(x: rect.width * Double(index) / Double(values.count - 1), y: rect.height * (0.9 - 0.8 * (value - low) / span))
                if index == 0 { path.move(to: point) } else { path.addLine(to: point) }
            }
        }
    }
}
