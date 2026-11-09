import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Linking,
  Platform,
  Settings,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { LiveActivity } from 'react-native-nitro-location-tracking';
import type {
  LiveActivityContent,
  LiveActivityIcon,
} from 'react-native-nitro-location-tracking';

// ─── Courier trip model (synthetic demo data) ─────────────────────────────────
//
// Mirrors the courier app: Assigned → On way → Arrived → Delivered.
// The card always shows the CURRENT order. While delivering, the next order is
// only hinted ("Keyingi: #…"). When the current order is delivered the card
// switches to the next one with update(); it ends only when no orders remain.

type Phase = 'assigned' | 'on_way' | 'arrived' | 'delivered';

type Order = Readonly<{
  id: string;
  number: string;
  phase: Phase;
  deadlineMs: number;
  restaurant: string;
  street: string; // street + house only — no client PII on the Lock Screen
  distanceMeters: number;
  startDistanceMeters: number;
}>;

const PHASE_ORDER: Phase[] = ['assigned', 'on_way', 'arrived', 'delivered'];

const LABEL: Record<Exclude<Phase, 'delivered'>, string> = {
  assigned: 'Buyurtma tayinlandi',
  on_way: "Yo'lda",
  arrived: 'Mijoz yonida',
};

const COLOR: Record<Exclude<Phase, 'delivered'>, string> = {
  assigned: '#FF9800',
  on_way: '#1E88E5',
  arrived: '#2E7D32',
};

const ICON: Record<Exclude<Phase, 'delivered'>, LiveActivityIcon> = {
  assigned: { type: 'symbol', name: 'takeoutbag.and.cup.and.straw.fill' },
  on_way: { type: 'symbol', name: 'car.fill' },
  arrived: { type: 'symbol', name: 'flag.checkered' },
};

const LATE_RED = '#C62828';

function formatDistance(m: number): string {
  return m >= 1000
    ? `${(m / 1000).toFixed(1).replace('.', ',')} km`
    : `${Math.round(m)} m`;
}

function makeTrip(now: number): Order[] {
  return [
    {
      id: '10423',
      number: '10423',
      phase: 'assigned',
      deadlineMs: now + 25 * 60_000,
      restaurant: 'Bellissimo Chilonzor',
      street: "Amir Temur ko'chasi, 15",
      distanceMeters: 3400,
      startDistanceMeters: 3400,
    },
    {
      id: '10424',
      number: '10424',
      phase: 'assigned',
      deadlineMs: now + 40 * 60_000,
      restaurant: 'Bellissimo Chilonzor',
      street: "Bunyodkor ko'chasi, 7",
      distanceMeters: 1900,
      startDistanceMeters: 1900,
    },
  ];
}

/** Pure: trip state → card content, or null when nothing is left to deliver. */
function toLiveActivity(
  orders: readonly Order[],
  now: number
): LiveActivityContent | null {
  const active = orders.filter((o) => o.phase !== 'delivered');
  const cur = active[0];
  if (!cur || cur.phase === 'delivered') return null;
  const next = active[1];
  const phase = cur.phase;

  const late = now > cur.deadlineMs;
  const accent = late ? LATE_RED : COLOR[phase];
  const minutesLate = Math.ceil((now - cur.deadlineMs) / 60_000);
  const icon = { ...ICON[phase], color: accent };

  return {
    title: late ? 'Kechikmoqda' : LABEL[phase],
    subtitle: `#${cur.number}`,
    icon,

    // Native countdown to the deadline; once late, a static "+N daq".
    timerEndEpochMs: late ? undefined : cur.deadlineMs,
    value: late ? `+${minutesLate}` : undefined,
    valueUnit: late ? 'daq' : undefined,
    secondaryValue:
      phase === 'on_way' ? formatDistance(cur.distanceMeters) : undefined,

    footerText: phase === 'assigned' ? cur.restaurant : cur.street,
    footerIcon: {
      type: 'symbol',
      name: phase === 'assigned' ? 'fork.knife' : 'mappin.and.ellipse',
    },
    footerTrailingText: next ? `Keyingi: #${next.number}` : undefined,

    progress:
      phase === 'on_way'
        ? 1 - cur.distanceMeters / cur.startDistanceMeters
        : undefined,
    accentColor: accent,

    compactLeading: { icon },
    compactTrailing: late
      ? { text: `+${minutesLate}`, color: LATE_RED }
      : phase === 'arrived'
      ? { icon: { type: 'symbol', name: 'mappin', color: accent } }
      : { timerEndEpochMs: cur.deadlineMs },
    minimal: { icon },

    staleText: 'Yangilanmoqda…',
    deepLinkUrl: `nitrolocation://delivery/${cur.id}`,
  };
}

const DELIVERED_FINAL: LiveActivityContent = {
  title: 'Barcha buyurtmalar yetkazildi',
  icon: { type: 'symbol', name: 'checkmark.seal.fill', color: '#2E7D32' },
  value: '✓',
  accentColor: '#2E7D32',
  compactLeading: {
    icon: { type: 'symbol', name: 'checkmark.seal.fill', color: '#2E7D32' },
  },
  compactTrailing: { text: '✓', color: '#2E7D32' },
};

// ─── Component ────────────────────────────────────────────────────────────────

export function LiveActivityDemo() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [info, setInfo] = useState('');

  const refreshInfo = useCallback(() => {
    setInfo(
      `supported=${LiveActivity.isSupported()} · enabled=${LiveActivity.areEnabled()} · running=${LiveActivity.isRunning()}`
    );
  }, []);
  useEffect(refreshInfo, [refreshInfo]);

  // Push trip state to the Live Activity: start / update / end.
  const push = useCallback(
    (next: Order[]) => {
      setOrders(next);
      const content = toLiveActivity(next, Date.now());
      try {
        if (!content) {
          LiveActivity.end({
            finalContent: DELIVERED_FINAL,
            dismissAfterSeconds: 5,
          });
        } else if (!LiveActivity.isRunning()) {
          LiveActivity.start(content);
        } else {
          LiveActivity.update(content);
        }
      } catch (e) {
        Alert.alert('Live Activity', String(e));
      }
      setTimeout(refreshInfo, 300);
    },
    [refreshInfo]
  );

  const assign = useCallback(() => push(makeTrip(Date.now())), [push]);

  // Advance the current order to its next status.
  const nextStatus = useCallback(() => {
    const idx = orders.findIndex((o) => o.phase !== 'delivered');
    if (idx < 0) return;
    const cur = orders[idx]!;
    const phase = PHASE_ORDER[PHASE_ORDER.indexOf(cur.phase) + 1]!;
    push(
      orders.map((o, i) => {
        if (i === idx) {
          return {
            ...o,
            phase,
            distanceMeters: phase === 'arrived' ? 0 : o.distanceMeters,
          };
        }
        // Courier leaves the restaurant with every order in the bag.
        if (o.phase === 'assigned' && phase === 'on_way') {
          return { ...o, phase: 'on_way' };
        }
        return o;
      })
    );
  }, [orders, push]);

  // Simulate GPS: courier moves 600 m closer to the current client.
  const drive = useCallback(() => {
    const idx = orders.findIndex((o) => o.phase !== 'delivered');
    if (idx < 0 || orders[idx]!.phase !== 'on_way') return;
    push(
      orders.map((o, i) =>
        i === idx
          ? { ...o, distanceMeters: Math.max(50, o.distanceMeters - 600) }
          : o
      )
    );
  }, [orders, push]);

  // Simulate a missed deadline for the current order.
  const makeLate = useCallback(() => {
    const idx = orders.findIndex((o) => o.phase !== 'delivered');
    if (idx < 0) return;
    push(
      orders.map((o, i) =>
        i === idx ? { ...o, deadlineMs: Date.now() - 4 * 60_000 } : o
      )
    );
  }, [orders, push]);

  // Logout / shift close: remove the card immediately.
  const reset = useCallback(() => {
    LiveActivity.end();
    setOrders([]);
    setTimeout(refreshInfo, 300);
  }, [refreshInfo]);

  // Deep links: tapping the card opens nitrolocation://delivery/<id>;
  // nitrolocation://demo/<action> drives the demo from a terminal
  // (adb shell am start -d …). Actions: assign, next, drive, late, reset.
  const actions = useRef({
    assign,
    next: nextStatus,
    drive,
    late: makeLate,
    reset,
  });
  actions.current = { assign, next: nextStatus, drive, late: makeLate, reset };
  useEffect(() => {
    const handle = (url?: string | null) => {
      const m = url?.match(/^nitrolocation:\/\/(demo|delivery)\/(\w+)/);
      if (!m) return;
      if (m[1] === 'delivery') {
        Alert.alert('Opened from Live Activity', `Order #${m[2]}`);
        return;
      }
      const action = actions.current[m[2] as keyof typeof actions.current];
      action?.();
    };
    Linking.getInitialURL().then(handle);
    const sub = Linking.addEventListener('url', (e) => handle(e.url));
    return () => sub.remove();
  }, []);

  // iOS test hook: `xcrun simctl launch <udid> <bundle> -LADemoStep 3`
  // assigns the trip, then presses "Next status" (N - 1) times.
  useEffect(() => {
    const target =
      Platform.OS === 'ios' ? Number(Settings.get('LADemoStep')) : 0;
    if (!target) return;
    const timers = [setTimeout(() => actions.current.assign(), 1500)];
    for (let i = 1; i < target; i++) {
      timers.push(setTimeout(() => actions.current.next(), 1500 + i * 700));
    }
    return () => timers.forEach(clearTimeout);
  }, []);

  const cur = orders.find((o) => o.phase !== 'delivered');
  const active = cur !== undefined;
  const now = Date.now();

  return (
    <View>
      <Text style={styles.info}>{info}</Text>
      {orders.map((o) => (
        <Text key={o.id} style={[styles.order, o === cur && styles.current]}>
          {o === cur ? '▶ ' : '   '}#{o.number} · {o.phase}
          {o.phase === 'on_way' ? ` · ${formatDistance(o.distanceMeters)}` : ''}
          {now > o.deadlineMs && o.phase !== 'delivered' ? ' · LATE' : ''}
        </Text>
      ))}
      {!orders.length && <Text>⚪ No orders</Text>}
      <View style={styles.row}>
        <Button title="Assign 2" onPress={assign} disabled={active} />
        <Button title="Next status" onPress={nextStatus} disabled={!active} />
        <Button
          title="Drive"
          onPress={drive}
          disabled={cur?.phase !== 'on_way'}
        />
      </View>
      <View style={styles.row}>
        <Button title="Make late" onPress={makeLate} disabled={!active} />
        <Button title="Logout (end)" onPress={reset} disabled={!active} />
      </View>
      <Text style={styles.muted}>
        iOS: go Home to see the Dynamic Island, or lock to see the Lock Screen
        card. Android: pull down the notification shade.
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  info: { fontFamily: 'Menlo', fontSize: 11, color: '#555', marginBottom: 6 },
  order: { fontFamily: 'Menlo', fontSize: 13, color: '#888' },
  current: { color: '#111', fontWeight: '600' },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    marginVertical: 6,
  },
  muted: { color: '#888', fontSize: 12 },
});
