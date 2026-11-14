import os
from pathlib import Path
import runpy
from unittest import TestCase, main
from unittest.mock import patch

OBS = runpy.run_path(str(Path(__file__).with_name('observe.py')))


class ObserverTest(TestCase):
    def fake(self, *args):
        self.calls.append(args[:2])
        if args[:2] == ('ecs', 'describe-services'):
            names = OBS['SERVICES']
            return {'services': [{'name': name} for name in names], 'failures': []}
        if args[:2] == ('ecs', 'list-tasks'):
            name = args[args.index('--service-name') + 1]
            return [] if name == 'identity-svc' and self.outage else [f'task:{name}']
        if args[:2] == ('ecs', 'describe-tasks'):
            count = 2 if self.outage else 3
            return {'tasks': [{} for _ in range(count)], 'failures': []}
        if args[:2] == ('sqs', 'get-queue-attributes'):
            return {'Attributes': {key: '0' for key in ('ApproximateNumberOfMessages',
                'ApproximateNumberOfMessagesNotVisible', 'ApproximateNumberOfMessagesDelayed')}}
        if args[:2] == ('cloudwatch', 'describe-alarms'):
            return [{'name': 'blocbuy-swe5001-notification-dlq-not-empty', 'state': 'OK'}]
        self.fail('Unexpected AWS operation')

    def setUp(self):
        self.calls, self.outage = [], False

    def test_all_services_and_task_identities_are_read_without_queue_receives(self):
        result = OBS['observation']('v5', self.fake)
        self.assertEqual(len(result['tasks']), 3)
        self.assertNotIn('queues', result)
        self.assertTrue(all(call in OBS['READS'] for call in self.calls))

    def test_identity_zero_is_a_valid_observation_not_a_collection_error(self):
        self.outage = True
        self.assertEqual(len(OBS['observation']('identity', self.fake)['tasks']), 2)

    def test_v7_observes_attributes_and_alarm_without_receiving_messages(self):
        result = OBS['observation']('v7', self.fake)
        self.assertEqual(len(result['queues']), 2)
        self.assertEqual(result['alarms'][0]['state'], 'OK')
        self.assertNotIn(('sqs', 'receive-message'), self.calls)

    def test_missing_service_and_queue_data_fail_closed(self):
        for response in ({'services': [], 'failures': []}, {'services': [], 'failures': [{'reason': 'MISSING'}]}):
            with self.assertRaises(ValueError):
                OBS['observation']('v5', lambda *args: response)
        def missing(*args):
            return {'Attributes': {}} if args[:2] == ('sqs', 'get-queue-attributes') else self.fake(*args)
        with self.assertRaises(KeyError):
            OBS['observation']('v7', missing)

    def test_unsupported_and_write_operations_are_refused_before_aws(self):
        for args in [('ecs', 'stop-task'), ('sqs', 'receive-message'), ('sns', 'publish'), ('ssm', 'get-parameter')]:
            with self.assertRaises(ValueError):
                OBS['read_aws'](*args)

    def test_cloudshell_and_unconfirmed_hosts_are_refused(self):
        with self.assertRaises(ValueError):
            OBS['SUPPORT']['guard_host'](False)
        with patch.dict(os.environ, {'AWS_EXECUTION_ENV': 'CloudShell'}):
            with self.assertRaises(ValueError):
                OBS['SUPPORT']['guard_host'](True)


if __name__ == '__main__':
    main()
