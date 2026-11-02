import json,pathlib,tempfile,unittest
from analyze_drive import analyze

class DriveReplayTests(unittest.TestCase):
    def test_last_session_and_fragments_use_production_parser(self):
        packet=[90,165,12,26,41,1,0,0,0,4,0,20,0,3,52,0,158]
        def row(t, data):return dict(type='update',synthetic=False,module=7,code=1019,profile=3014908,timeMs=t,ints=data)
        rows=[dict(type='reader_configuration',timeMs=1),row(2,packet),dict(type='reader_configuration',timeMs=100),row(101,packet[:8]),row(102,packet[8:])]
        with tempfile.TemporaryDirectory() as tmp:
            path=pathlib.Path(tmp)/'test.jsonl';path.write_text('\n'.join(map(json.dumps,rows)),encoding='utf-8')
            result=analyze(path)
        self.assertEqual(result['selected_session_index'],1)
        self.assertEqual(result['validated_packets'],1)
        self.assertEqual(result['raw_callbacks'],2)
        self.assertEqual(result['rpm']['min'],820)
        self.assertEqual(result['psa_sequence'][0]['seconds'],0.002)
        self.assertEqual(result['steering_raw']['max'],20)
