/* Render only the server-owned chart data protocol. Never evaluate a caller-supplied option script. */
const fs = require('node:fs');
const echarts = require('echarts');

const input = JSON.parse(fs.readFileSync(0, 'utf8'));
const width = Number(input.width);
const height = Number(input.height);
if (!Number.isFinite(width) || !Number.isFinite(height) || width < 100 || height < 100 || width > 1600 || height > 1600) {
  throw new Error('invalid chart dimensions');
}
const palettes = {
  DEFAULT: ['#5470c6', '#91cc75', '#fac858', '#ee6666'],
  BUSINESS_BLUE: ['#184c8b', '#2f79b8', '#489fc0', '#68b7b6'],
  MONOCHROME: ['#2d2d2d', '#6e6e6e', '#aaaaaa', '#d7d7d7']
};
const palette = palettes[input.stylePreset];
if (!palette) throw new Error('invalid chart style preset');
const categories = input.categories;
const series = input.series;
if (!Array.isArray(categories) || !Array.isArray(series) || !categories.length || !series.length) {
  throw new Error('chart data is required');
}
const base = { animation: false, backgroundColor: '#ffffff', color: palette,
  textStyle: { fontFamily: 'Arial, sans-serif', color: '#253044', fontSize: 14 },
  title: { text: input.title || '', left: 'center' },
  legend: { show: Boolean(input.legendVisible), bottom: 0 } };
if (input.chartType === 'PIE') {
  base.series = [{ type: 'pie', radius: ['25%', '65%'], center: ['50%', '53%'],
    label: { show: true, formatter: input.showPercentages ? '{b}: {d}%' : '{b}: {c}' },
    data: categories.map((name, index) => ({ name, value: series[0].values[index] })) }];
} else if (input.chartType === 'RADAR') {
  const maximum = Math.max(1, ...series.flatMap(item => item.values)) * 1.15;
  base.radar = { indicator: categories.map(name => ({ name, max: maximum })) };
  base.series = [{ type: 'radar', data: series.map(item => ({ name: item.name, value: item.values })) }];
} else {
  const horizontal = input.chartType === 'BAR';
  base.grid = { top: input.title ? 60 : 28, left: 55, right: 22, bottom: input.legendVisible ? 55 : 30,
    containLabel: true };
  base.xAxis = horizontal ? { type: 'value', name: input.valueAxisTitle || '' }
    : { type: 'category', data: categories, name: input.categoryAxisTitle || '' };
  base.yAxis = horizontal ? { type: 'category', data: categories, name: input.categoryAxisTitle || '' }
    : { type: 'value', name: input.valueAxisTitle || '' };
  base.series = series.map(item => ({ name: item.name || '',
    type: input.chartType === 'LINE' ? 'line' : 'bar',
    smooth: input.chartType === 'LINE',
    itemStyle: { borderRadius: input.chartType === 'LINE' ? 0 : 3 },
    areaStyle: input.chartType === 'LINE' ? { opacity: 0.08 } : undefined,
    label: { show: Boolean(input.showValues), position: horizontal ? 'right' : 'top' },
    data: item.values }));
}
const chart = echarts.init(null, null, { renderer: 'svg', ssr: true, width, height });
try {
  chart.setOption(base);
  process.stdout.write(chart.renderToSVGString());
} finally {
  chart.dispose();
}
