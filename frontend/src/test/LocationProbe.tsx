import { useLocation } from 'react-router'

// 今の URL を画面に書き出す（テストで、画面が移ったかを確かめるため）
export function LocationProbe() {
  const location = useLocation()
  return <output data-testid="location">{location.pathname + location.search}</output>
}
