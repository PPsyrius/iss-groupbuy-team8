from array import array
import unittest
from timing_analysis import percentile,request_key,request_record,REQUIRED,stats,join_point,EXPORT_ORDER

class TimingTest(unittest.TestCase):
    def test_interpolated_percentile(self):
        self.assertAlmostEqual(percentile([0,10],.95),9.5)
        self.assertIsNone(percentile([],.95))

    def test_no_double_count_of_connection_phases(self):
        values = dict.fromkeys(REQUIRED,0)
        values.update(http_reqs=1,http_req_duration=100,http_req_blocked=20,http_req_connecting=5,http_req_tls_handshaking=10,http_req_waiting=98)
        item = request_record(('2026-11-11T08:16:03.123456789Z','product list','200','scaling'),values)
        self.assertEqual(item['totalHttpMs'],120)
        self.assertAlmostEqual(item['end']-item['start'],.12,places=5)

    def test_missing_component_refused(self):
        with self.assertRaises(AssertionError):
            request_record(('2026-11-11T08:16:03Z','product list','0','scaling'),{'http_reqs':1})

    def test_route_and_status_remain_distinct(self):
        a={'time':'2026-11-11T08:16:03Z','tags':{'name':'product list','status':'200','scenario':'scaling'}}
        b={'time':a['time'],'tags':{**a['tags'],'status':'503'}}
        self.assertNotEqual(request_key(a),request_key(b))

    def test_negative_phase_values_preserved_not_clamped(self):
        result=stats(array('d',[-1,0,10]))
        self.assertEqual(result['negativeValues'],1)
        self.assertEqual(result['meanMs'],3)

    def test_repeated_timestamp_batches_not_deduplicated(self):
        pending={}
        results=[]
        key=('2026-11-11T08:16:03Z','product list','200','scaling')
        for duration in (10,100):
            for metric in EXPORT_ORDER:
                result=join_point(pending,key,metric,duration if metric=='http_req_duration' else 1)
                if result is not None:
                    results.append(result)
        self.assertEqual([r['http_req_duration'] for r in results],[10,100])
        self.assertEqual(pending,{})

    def test_ambiguous_batch_order_refused(self):
        with self.assertRaises(AssertionError):
            join_point({},('time','route','200','scaling'),'http_req_waiting',1)

if __name__ == '__main__':
    unittest.main()
