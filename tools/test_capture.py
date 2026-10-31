import json
import unittest
from analyze_capture import summarize, profile_parts, psa_temperature, mcu_payload


class EvidenceRegressionTests(unittest.TestCase):
    def test_database_profiles(self):
        # Independent values from Update.apk/assets/canbus.db, HIWORLD 508 2010-18 High.
        self.assertEqual(profile_parts(3014908), {'value':3014908,'protocol':252,'variant':46})

    def test_oem_display_formula_and_special_values(self):
        # Decompiled UI divides by ten AFTER adding the separately received offset.
        self.assertEqual(psa_temperature(225,2),24.5)
        self.assertEqual(psa_temperature(-2,2),'LOW')
        self.assertIsNone(psa_temperature(225,None))

    def test_synthetic_and_cross_profile_values_are_not_combined(self):
        def event(profile,code,value,synthetic=False):
            return json.dumps({'type':'update','module':7,'synthetic':synthetic,
                               'profile':profile,'code':code,'ints':[value]})
        result=summarize([event(3014908,27,225),event(3014908,242,2),
                          event(2949372,27,200),event(2949372,242,0),
                          event(3014908,161,6500,True)])
        self.assertEqual(result['accepted_callbacks'],4)
        self.assertEqual(result['rejected_non_probe_events'],1)
        self.assertEqual([t['oem_display_value'] for t in result['psa252_temperature_display']],[24.5,20.0])
        self.assertNotIn(161,result['counts_by_code'])

    def test_raw_signed_bytes_and_initial_snapshot(self):
        self.assertEqual(mcu_payload([-29,-1,127])['unsigned'],[227,255,127])
        self.assertEqual(mcu_payload([0])['kind'],'snapshot_or_zero_payload')
        self.assertEqual(mcu_payload([])['kind'],'empty')

    def test_same_code_from_distinct_modules_is_not_merged(self):
        lines=[json.dumps({'type':'update','synthetic':False,'module':m,'code':4,'ints':[1],'profile':2949372}) for m in [0,7]]
        result=summarize(lines)
        self.assertEqual(result['counts_by_module_and_code'],{'0:4':1,'7:4':1})
        self.assertEqual(result['counts_by_code'],{4:1})


if __name__=='__main__':
    unittest.main()
