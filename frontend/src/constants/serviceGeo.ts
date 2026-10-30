/** Coordinates are confirmed by the user; they approximate distance, not travel time.
 * Source for geocoding: OpenStreetMap contributors / Nominatim (manual queries only).
 */
export type Coordinates = {lat:number;lon:number;label:string;wardCode?:string;proof?:string};
export const RADII = [5,10,15,20,30,40,60] as const;
export function formatPoint(province:string,point:Coordinates) {
  if (!/^\d{2}$/.test(province) || !Number.isFinite(point.lat) || !Number.isFinite(point.lon)) return '';
  return `${province}|${point.lat.toFixed(5)}|${point.lon.toFixed(5)}${point.wardCode&&point.proof?`|${point.wardCode}|${point.proof}`:""}`;
}
export function parseLocation(value: string): {province:string;coords:Coordinates|null; radius:number} {
  const chunks = value.split('|'); const province=chunks[0] || '';
  const lat=Number(chunks[1]),lon=Number(chunks[2]);
  const valid=chunks.length >= 3 && Number.isFinite(lat) && Number.isFinite(lon) && lat>=7 && lat<=24 && lon>=101 && lon<=115;
  return {province,coords:valid?{lat,lon,label:`${lat.toFixed(5)}, ${lon.toFixed(5)}`}:null,
    radius:RADII.includes(Number(chunks[3]) as typeof RADII[number])?Number(chunks[3]):15};
}
